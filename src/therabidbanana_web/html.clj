(ns therabidbanana-web.html
  (:require [hiccup2.core :as h]
            [therabidbanana-web.layouts :as layouts]
            [hiccup.compiler])
  (:import [clojure.lang IPersistentVector]))

;;; Following are hacks to Hiccup to override render-element and add whitespace
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
    (cond
      ;; Not sure why nextjournal markdown is adding these
      (= "<>" tag)
      (hiccup.compiler/render-html content)
      (and (container-tag? tag content) (not (#{"pre" "code"} tag)))
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

(extend-protocol hiccup.compiler/HtmlRenderer
  IPersistentVector
  (render-html [this]
    (render-element this)))

;; End of Hiccup hacks to add newlines

(defn- coerce-page [page-or-str]
  (if (string? page-or-str)
    {:header {} :body page-or-str}
    page-or-str))

(defn as-html [page-or-str]
  (let [{:keys [header]
         :as   page} (coerce-page page-or-str)
        layout-name  (get header :layout "main")
        layout       (get layouts/by-name layout-name layouts/main)]
    (str (h/html {:mode :html} (layout page)))))
