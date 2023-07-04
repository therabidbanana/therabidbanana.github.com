(ns therabidbanana-web.core
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs]
            [ring.middleware.resource :refer [wrap-resource]]
            [therabidbanana-web.markdown :refer [parse-markdown]]
            [therabidbanana-web.html :refer [as-html]]
            [stasis.core :as stasis]))

(defn markdown-pages [pages dir]
  (zipmap (map #(as-> % $
                  ;; TODO - why doesn't index replace?
                  (str/replace $ #"(index\.md|index\.markdown)$" "")
                  (str/replace $ #"(\.md|\.markdown)$" "/")
                  (str/replace $ #"^/(\d{4})(-\d{2}-\d{2})?-" "/$1/")
                  (str dir $))
               (keys pages))
          (map #(fn [req] (-> % parse-markdown as-html))
               (vals pages))))

(defn partial-pages [pages]
  (zipmap (map #(str/replace % #".(html)$" "/")
               (keys pages))
          (map #(fn [req] (as-html %)) (vals pages))))

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
