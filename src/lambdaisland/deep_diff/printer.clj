(ns lambdaisland.deep-diff.printer
  (:require [fipp.engine :as fipp]
            [fipp.visit :as fv]
            [puget.color :as color]
            [puget.dispatch]
            [puget.printer :as puget]
            [arrangement.core]
            [lambdaisland.deep-diff.diff :as diff]))

(defn print-deletion [printer expr]
  (let [no-color (assoc printer :print-color false)]
    (color/document printer ::deletion [:span "-" (puget/format-doc no-color (:- expr))])))

(defn print-insertion [printer expr]
  (let [no-color (assoc printer :print-color false)]
    (color/document printer ::insertion [:span "+" (puget/format-doc no-color (:+ expr))])))

(defn print-mismatch [printer expr]
  [:group
   [:span ""] ;; needed here to make this :nest properly in kaocha.report/print-expr '=
   [:align
    (print-deletion printer expr) :line
    (print-insertion printer expr)]])

(defn print-other [printer expr]
  (let [no-color (assoc printer :print-color false)]
    (color/document printer ::other [:span "-" (puget/format-doc no-color expr)])))

(defn- map-handler [this value]
  (let [ks (#'puget/order-collection (:sort-keys this) value (partial sort-by first arrangement.core/rank))
        entries (map (partial puget/format-doc this) ks)]
    [:group
     (color/document this :delimiter "{")
     [:align (interpose [:span (:map-delimiter this) :line] entries)]
     (color/document this :delimiter "}")]))

(def ^:private print-handlers
  {'lambdaisland.deep_diff.diff.Deletion
   print-deletion

   'lambdaisland.deep_diff.diff.Insertion
   print-insertion

   'lambdaisland.deep_diff.diff.Mismatch
   print-mismatch

   'clojure.lang.PersistentArrayMap
   map-handler

   'clojure.lang.PersistentHashMap
   map-handler

   'clojure.lang.MapEntry
   (fn [printer value]
     (let [k (key value)
           v (val value)]
       (let [no-color (assoc printer :print-color false)]
         (cond
           (instance? lambdaisland.deep_diff.diff.Insertion k)
           [:span
            (print-insertion printer k)
            (if (coll? v) (:map-coll-separator printer) " ")
            (color/document printer ::insertion (puget/format-doc no-color v))]

           (instance? lambdaisland.deep_diff.diff.Deletion k)
           [:span
            (print-deletion printer k)
            (if (coll? v) (:map-coll-separator printer) " ")
            (color/document printer ::deletion (puget/format-doc no-color v))]

           :else
           [:span
            (puget/format-doc printer k)
            (if (coll? v) (:map-coll-separator printer) " ")
            (puget/format-doc printer v)]))))})

(defn- print-handler-resolver [extra-handlers]
  (fn [^Class klz]
    (and klz (get (merge @#'print-handlers extra-handlers)
                  (symbol (.getName klz))))))

(defn register-print-handler!
  "Register an extra print handler.

  `type` must be a symbol of the fully qualified class name. `handler` is a
  Puget handler function of two arguments, `printer` and `value`."
  [type handler]
  (alter-var-root #'print-handlers assoc type handler))

(defn puget-printer
  ([]
   (puget-printer {}))
  ([opts]
   (let [extra-handlers (:extra-handlers opts)]
     (puget/pretty-printer (merge {:width          (or *print-length* 100)
                                   :print-color    true
                                   :color-scheme   {::deletion  [:red]
                                                    ::insertion [:green]
                                                    ::other     [:yellow]
                                                    ;; puget uses green and red for
                                                    ;; boolean/tag, but we want to reserve
                                                    ;; those for diffed values.
                                                    :boolean    [:bold :cyan]
                                                    :tag        [:magenta]}
                                   :print-handlers  (print-handler-resolver extra-handlers)}
                                  (dissoc opts :extra-handlers))))))

(defn format-doc [expr printer]
  (puget/format-doc printer expr))

(defn print-doc [doc printer]
  (fipp.engine/pprint-document doc {:width (:width printer)}))
