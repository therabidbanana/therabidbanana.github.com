(defproject therabidbanana-web "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [stasis "2023.06.03"]
                 [hiccup "1.0.5"]
                 ;; Graal issue?
                 ;; [io.github.nextjournal/markdown "0.5.144"]
                 [markdown-clj "1.11.4"]
                 [ring "1.10.0"]]
  :ring {:handler therabidbanana-web.core/app}
  :profiles {:dev {:plugins [[lein-ring "0.12.6"]]}}
  :repl-options {:init-ns therabidbanana-web.core})
