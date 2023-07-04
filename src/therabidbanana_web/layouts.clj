(ns therabidbanana-web.layouts
  (:require [hiccup2.core :as h]))

(defn main [{:keys [header body] :as page}]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1.0"}]
    [:title "David Haslem"]
    [:link {:rel "stylesheet" :href "/highlight/styles/dark.min.css"}]
    [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Bevan:regular"}]
    [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Open+Sans:regular"}]
    [:script {:src "/highlight/highlight.min.js"}]
    [:script {} "hljs.highlightAll()"]
    [:link {:rel "stylesheet" :href "/assets/main.css"}]]
   [:body
    [:div#container {}
     [:main
      [:nav
       [:h1 [:a {:href "/"} "David Haslem ↑"]]]
      [:article
       {}
       (if (:title header)
         [:header {} [:h1 {} (:title header)]])
       [:section {}
        (if (string? body)
          (h/raw body)
          body)]]
      ]
     [:div.sidebar {}]]
    ]])

(def by-name {"main" main})
