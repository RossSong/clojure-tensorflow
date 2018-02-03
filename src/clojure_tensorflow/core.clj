(ns clojure-tensorflow.core
  (:require [clojure-tensorflow.utils :as utils]
            [clojure-tensorflow.ops :as ops]
            [clojure-tensorflow.graph
             :refer [graph global-variables shadow-graph shadow-graph']]
            [clojure-tensorflow.session :refer [session]]
            [clojure.spec.alpha :as s]
            [clojure-tensorflow.gradients :as grad]
            [autodiff.protocols :as ad]
            [clojure-tensorflow.build :as build]
            [clojure-tensorflow.ops :as tf])
  (:import [autodiff.protocols.Dual]))



(defn feed
  "Feed value to placeholder
  Pass a map of locations to values"
  ([runner feed-map]
   (doall (map build/build-op (keys feed-map)))
   (utils/thread
     runner
     (map (fn [[key val]]
            #(.feed % (name key) (utils/clj->tensor val))) feed-map))))


(defmulti get-name class)
(defmethod get-name org.tensorflow.Output [o] (-> o .op .name))
(defmethod get-name org.tensorflow.Operation [op] (-> op .name))


(defn run
  "Call session runner on single op.
  Returns tensor object"
  ([op-name] (run op-name {}))
  ([op feed-map]
   ;; {:pre [(s/valid? ::op-name op-name)]}
   (if (coll? op)
     ;; if run on AutoDiff Dual type, run function and its derivative
     (if (= (type op) (type (ad/->Dual 1 1)))
       (-> op
           (update :f run)
           (update :f' run))
       ;; if run on a list of operations, run all and return the last
       (do
         (doall (map build/build-op (flatten op)))
         ;; (-> session .runner (feed feed-map))
           (last (map #(run % feed-map) (flatten op)))))
     ;; if run on a single op return it
     (-> session
         .runner
         (feed feed-map)
         (.fetch (get-name (build/build-op op)))
         .run
         (.get 0)
         utils/tensor->clj
         )
     )))



(defmacro with-graph [& body]
  `(binding [graph (org.tensorflow.Graph.)
             global-variables (atom [])
             shadow-graph (atom [])
             shadow-graph' (atom {})]
     (try ~@body
       (finally (.close graph)))))

(defmacro with-this-graph [g & body]
  `(binding [graph (org.tensorflow.Graph.)
             global-variables (atom [])
             shadow-graph' (atom ~g)]
     (try ~@body
          (finally (.close graph)))))

(defmacro with-session [& body]
  `(binding [session (org.tensorflow.Session. graph)]
     (try ~@body
       (finally (.close session)))))


(utils/clj->tensor [3.0])
(with-this-graph
  (->
   {:x {:operation :Placeholder
        :attrs {:dtype (.dataType (utils/clj->tensor [3.0]))
                ;; :value (utils/clj->tensor [3])
                }}
    :y {:operation :Const
        :attrs {:dtype (.dataType (utils/clj->tensor [2.]))
                :value (utils/clj->tensor [2.])
                }}
    :z {:operation :Mul :inputs [:x :y]}
    :a {:operation :Mul :inputs [:x :z]}
    })


  ;; (with-session
  ;;   (run (gradient :a :y)))

  ;; (grad/path :x :y)
  (with-session
    (run
      (ad/coerce
       (tf/constant 20.3))
      ))
  ;; (with-session
  ;;   (run :z {:x 2.}))
  ;; (with-session
  ;;   (run :z {:x [1.]}))

  ;; ((grad/ancestors :z) :y)

  )
