(ns therabidbanana-web.markdown
  (:require [hiccup2.core :as h]
            [hiccup.compiler]
            [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [ring.middleware.resource :refer [wrap-resource]]
            [garden.core :refer [css]]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]
            [stasis.core :as stasis])
  (:import [clojure.lang IPersistentVector ISeq Named]))

(defmethod nextjournal.markdown.parser/apply-token "html_block" [doc {:as _token c :content}]
  (-> doc
      (nextjournal.markdown.parser/open-node :raw-html)
      (nextjournal.markdown.parser/push-node (nextjournal.markdown.parser/text-node c))
      nextjournal.markdown.parser/close-node))

(defmethod nextjournal.markdown.parser/apply-token "html_inline" [doc {:as _token c :content}]
  (-> doc
      (nextjournal.markdown.parser/open-node :raw-html)
      (nextjournal.markdown.parser/push-node (nextjournal.markdown.parser/text-node c))
      nextjournal.markdown.parser/close-node))

(def markdown-renderer
  (assoc md.transform/default-hiccup-renderers
         ;; :doc specify a custom container for the whole doc
         ;; :doc (partial md.transform/into-markup [:div])
         ;; :plain fragments might be nice, but paragraphs help when no reagent is at hand
         :raw-html (fn [conf el] (h/raw (get-in el [:content 0 :text])))
         :footnote-ref (fn [conf el]
                         ;; [:code {} (get-in conf [:footnotes (:ref el)])]
                         (md.transform/into-markup [:aside]
                                                   conf
                                                   (get-in conf [:footnotes (:ref el)]))
                         )
         :code (fn [conf el]
                 (if (= (:language el) "aside")
                   [:pre [:code {:class (str "language-" (:language el))}
                          (get-in el [:content 0 :text])]]))
         ))

(defn markdown-render [struct]
  (md.transform/->hiccup (assoc markdown-renderer :footnotes (:footnotes struct))
                         struct)
  ;; [:code {} struct]
  )

(defn parse-markdown [string]
  (let [has-header?     (str/starts-with? string "---")
        [_ header string] (if has-header?
                          (str/split string #"---" 3)
                          ["" "" string])
        parsed-header     (if has-header?
                            (yaml/parse-string header)
                            {})]
    (->> (md/parse string)
         (markdown-render)
         (assoc {:header parsed-header} :body))))

