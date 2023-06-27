(ns therabidbanana-web.core
  (:require [hiccup.page :refer [html5] :as h]
            [clojure.java.io :as io]
            [markdown.core :as mk]
            ;; [nextjournal.markdown :as md]
            ;; [nextjournal.markdown.transform :as md.transform]
            [stasis.core :as stasis]))

(defn layout-page [page]
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1.0"}]
    [:title "David Haslem"]
    [:link {:rel "stylesheet" :href "/styles/styles.css"}]]
   [:body
    [:div.logo "davidhaslem.com"]
    [:div.body page]]))


(defn markdown-pages [pages]
  (zipmap (keys pages)
          (map #(-> %1 mk/md-to-html-string layout-page) (vals pages))))

(defn partial-pages [pages]
  (zipmap (keys pages)
          (map layout-page (vals pages))))

(defn get-pages []
  (stasis/merge-page-sources
   {:public (stasis/slurp-directory "resources/public" #".*\.(html|css|js)$")
    :md-pages  (markdown-pages (stasis/slurp-directory "resources/pages" #".*\.(md|markdown)$"))
    :pages  (partial-pages (stasis/slurp-directory "resources/pages" #".*\.html$"))}))

(def app (stasis/serve-pages get-pages))
