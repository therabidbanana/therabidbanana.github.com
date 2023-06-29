(ns therabidbanana-web.core
  (:require [hiccup2.core :as h]
            [hiccup.compiler]
            [clojure.java.io :as io]
            [clojure.string :as str]
            ;; [markdown.core :as mk]
            [me.raynes.fs :as fs]
            [ring.middleware.resource :refer [wrap-resource]]
            [garden.core :refer [css]]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]
            [stasis.core :as stasis])
  (:import [clojure.lang IPersistentVector ISeq Named]))

(def ^{:doc "A list of elements that must be rendered without a closing tag."
       :private true}
  void-tags
  #{"area" "base" "br" "col" "command" "embed" "hr" "img" "input" "keygen" "link"
    "meta" "param" "source" "track" "wbr"})

(defn- container-tag?
  "Returns true if the tag has content or is not a void tag. In non-HTML modes,
  all contentless tags are assumed to be void tags."
  [tag content]
  (or content
      (not (void-tags tag))))

(defn- end-tag [] " />")

(defn render-element
  "Render an element vector as a HTML element."
  [element]
  (let [[tag attrs content] (hiccup.compiler/normalize-element element)]
    (cond (and (container-tag? tag content) (not (#{"pre" "code"} tag)))
      (str "<" tag (hiccup.compiler/render-attr-map attrs) ">\n"
           (hiccup.compiler/render-html content)
           "\n"
           "</" tag ">"
           "\n")
      (container-tag? tag content)
      (str "<" tag (hiccup.compiler/render-attr-map attrs) ">"
           (hiccup.compiler/render-html content)
           "</" tag ">"
           "\n")
      :else
      (str "<" tag (hiccup.compiler/render-attr-map attrs) (end-tag) "\n"))))

;; Hiccup hacks to add newlines.... do we want it?
(extend-protocol hiccup.compiler/HtmlRenderer
  IPersistentVector
  (render-html [this]
    (render-element this)))

(defn layout-page [page]
  (str
   (h/html {:mode :html}
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport"
              :content "width=device-width, initial-scale=1.0"}]
      [:title "David Haslem"]
      [:link {:rel "stylesheet" :href "/highlight/styles/dark.min.css"}]
      [:script {:src "/highlight/highlight.min.js"}]
      [:script {} "hljs.highlightAll()"]
      [:link {:rel "stylesheet" :href "/assets/main.css"}]]
     [:body.main
      [:main
       [:nav ]
       [:article
        (if (string? page)
          (h/raw page)
          page)]
       ]
      [:div.sidebar {}]
      ]])))


(def markdown-renderer
  (assoc md.transform/default-hiccup-renderers
         ;; :doc specify a custom container for the whole doc
         :doc (partial md.transform/into-markup [:div])
         ;; :plain fragments might be nice, but paragraphs help when no reagent is at hand
         :footnote-ref (fn [conf el]
                         ;; [:code {} (get-in conf [:footnotes (:ref el)])]
                         (md.transform/into-markup [:aside]
                                                   conf
                                                   (get-in conf [:footnotes (:ref el)]))
                         )
         :code (fn [conf el]
                 (if (= (:language el) "aside")
                   [:pre [:code {:class (str "language-" (:language el))}
                          (get-in el [:content 0 :text])]]))))

(defn markdown-render [struct]
  (md.transform/->hiccup (assoc markdown-renderer :footnotes (:footnotes struct))
                         struct)
  ;; [:code {} struct]
  )

(defn parse-markdown [string]
  (-> (md/parse string)
      (markdown-render)))

(defn markdown-pages [pages dir]
  (zipmap (map #(as-> % $
                  (str/replace $ #".(md|markdown)$" "/")
                  (str/replace $ #"^/(\d{4})(-\d{2}-\d{2})?-" "/$1/")
                  (str dir $) )
               (keys pages))
          (map #(fn [req] (-> % parse-markdown layout-page))
               (vals pages))))

(defn partial-pages [pages]
  (zipmap (map #(str/replace % #".(md|markdown)$" "/")
               (keys pages))
          (map #(fn [req] (layout-page %)) (vals pages))))

(defn get-pages []
  (stasis/merge-page-sources
   {:public (stasis/slurp-directory "resources/public" #".*\.(html|css|js)$")
    :md-pages  (markdown-pages (stasis/slurp-directory "resources/pages" #".*\.(md|markdown)$") "")
    :md-posts  (markdown-pages (stasis/slurp-directory "resources/posts" #".*\.(md|markdown)$") "/blog")
    :pages  (partial-pages (stasis/slurp-directory "resources/pages" #".*\.html$"))}))

(def app (wrap-resource (stasis/serve-pages get-pages) "public"))

(def export-dir "dist")

(defn export []
  (stasis/empty-directory! export-dir)
  (fs/copy-dir-into "resources/public" export-dir)
  (stasis/export-pages (get-pages) export-dir)
  )
