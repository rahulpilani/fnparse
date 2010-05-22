(ns edu.arizona.fnparse.common
  "This is a namespace *not* to be used by users of FnParse.
  It is a library that contains \"private\" functions that
  are still common to both FnParse Hound and Cat."
  {:author "Joshua Choi", :skip-wiki true}
  (:require [clojure.contrib [def :as d] [string :as str] [seq :as seq]]
            [clojure.set :as set] [edu.arizona.fnparse.core :as c])
  (:refer-clojure :exclude #{find}))

(d/defvar- rule-doc-summary-header
  "\n
  Rule Summary
  ============")

(d/defvar- rule-doc-info
  {:succeeds "Success"
   :product "Product"
   :consumes "Consumes"
   :error "Error"
   :description "Description"})

(defn rule-doc-str [doc-str meta-opts description]
  (let [doc-str (or doc-str "No description available.")
        doc-opts (select-keys meta-opts (keys rule-doc-info))
        opt-seq (seq doc-opts)
        doc-opts (assoc doc-opts :description description)]
    (if opt-seq
      (->> doc-opts sort
        (map #(format "  * %s: %s" (rule-doc-info (key %)) (val %)))
        (interpose "\n")
        (apply str doc-str rule-doc-summary-header "\n"))
      doc-str)))

(defmacro make-normal-rule-wrapper [type rule-symbol inner-fn-body]
  {:pre [(keyword? type) (symbol? rule-symbol)]}
  (let [rule-kw (keyword rule-symbol)]
   `(let [inner-body-delay# (delay ~inner-fn-body)]
      (with-meta (fn [] (force inner-body-delay#))
        (c/make-normal-rule-meta ~type ~rule-kw)))))

(defmacro make-named-rule-wrapper [type rule-form]
  {:pre [(keyword? type)]}
 `(let [rule-delay# (delay ~rule-form)]
    (with-meta (fn named-rule [] (force ((force rule-delay#))))
      (c/make-named-rule-meta ~type (delay (meta (force rule-delay#)))))))

(defmacro make-rule [type rule-symbol state-symbol & body]
  {:pre [(symbol? rule-symbol) (keyword? type)]}
 `(make-normal-rule-wrapper ~type ~rule-symbol
    (fn ~rule-symbol [~state-symbol] ~@body)))

(defmacro general-defrule
  [rule-sym description doc-string meta-opts type form]
  {:pre [(string? description)
         (or (string? doc-string) (nil? doc-string))
         (or (map? meta-opts) (nil? meta-opts))]}
 `(let [rule# (make-named-rule-wrapper ~type ~form)
        rule-var# (d/defvar ~rule-sym rule# ~doc-string)]
    (alter-meta! rule-var# update-in [:doc]
      rule-doc-str ~meta-opts ~description)
    rule-var#))

(defmacro general-defmaker [def-form description fn-name & forms]
 `(let [maker-var# (~def-form ~fn-name ~@forms)]
    (alter-var-root maker-var# identity)
    ; Add extended documentation.
    (alter-meta! maker-var# update-in [:doc]
      rule-doc-str (meta maker-var#) ~description)
    ; Memoize unless the :no-memoize meta flag is true.
    (when-not (:no-memoize? (meta maker-var#))
      (alter-var-root maker-var# memoize))
    maker-var#))

(defn merge-parse-errors
  "Merges two ParseErrors together. If the two errors are at the same
  position, their descriptors are combined. If one of the errors
  is at a further position than the other, than that first error
  is returned instead."
  [error-a error-b]
  (let [{position-a :position, descriptors-a :descriptors} error-a
        {position-b :position, descriptors-b :descriptors} error-b]
    (cond
      (or (> position-b position-a) (empty? descriptors-a)) error-b
      (or (< position-b position-a) (empty? descriptors-b)) error-a
      true (assoc error-a :descriptors
             (set/union descriptors-a descriptors-b)))))

(defn assoc-label-in-descriptors
  "Removes all labels from the given `descriptors` set, then adds the
  given `label-str`."
  [descriptors lbl]
  {:pre #{(set? descriptors)}, #_:post #_ [(set? %)]}
  (let [descriptors (set/select (complement c/label-descriptor?) descriptors)
        new-label-descriptor (c/make-label-descriptor lbl)
        descriptors (conj descriptors new-label-descriptor)]
    descriptors))
