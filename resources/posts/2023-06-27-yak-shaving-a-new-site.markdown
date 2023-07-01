---
title: "Yak Shaving a New Website"
---

Blogging is always something that I want to start doing more, get going with one or two posts, then fizzle out with. Looking back through the history, it seems like I'm a cicada, poking my head out every 5 to 7 years with a couple of posts and then leaving the links behind as I go back to other projects.

I've had DavidHaslem.com for well over 10 years as a personal website, but for the most part I've left it to stagnate. The previous iteration, over 7 years old, was an "Octopress" based blog, built on Jekyll to support Github Pages. I recently encountered a post on Mastodon that made me want to start building out my site again, but unfortunately making a commit to the repo didn't build as I expected and thus started my yak shave....


1. The build was failing, why? Probably because the Ruby versions were so old, can I fix the workflow?
2. Github Pages still doesn't update it... where is DNS again?
3. This was a Dreamhost site? I still have Dreamhost? Should shut that down - how do I get the files?
4. Maybe while I'm here let's pick a new way to render markdown files
5. Ooh, this one from the Clojure community sounds fun! It doesn't do anything!

My new website is set up to use the Stasis library, mostly following along with the tutorial I found here: <https://cjohansen.no/building-static-sites-in-clojure-with-stasis>. (I already had Leiningen installed and some experience with Clojure so it was pretty easy to follow)

## HTML generation

Of course, copying static HTML templates is a bit more annoying than I'd like, so the first step is tying together Stasis with Hiccup for Clojure based HTML rendering. This is pretty much a straight copy of the article I was following along with, but with two minor changes: 1) using hiccup2, which has a few minor API changes and 2) adding a small patch to add whitespace to the rendering (Hiccup outputs on a single line if you let it).


## Markdown Parsing

Stasis is mostly just a helper library for gathering files and assigning them URL paths - to do something like render Markdown you have to take the files given and pass them into another library. Here I've set up the system to use Nextjournal's Markdown library - https://github.com/nextjournal/markdown - I've used Clerk before during Advent of Code and really enjoyed it, so I figured it'd be a good basis for modern Markdown rendering - it returns a parsed tree of nodes and then can generate Hiccup, which means it works great as an extension to the first step. Mostly the logic stayed the same as in the article but swapping generating an HTML string to generating Hiccup markup instead.

## Github Workflow for Stasis

I couldn't find any direct examples of this, but there were some similar ones for Hugo that I could reuse after smashing up something for Clojure workflows with it.

## DNS

Of course, Dreamhost was holding my DNS still so it took an extra day to get it back to my registrar to redirect toward Pages and actually set up the Github side of the configuration.

So after a week from me first trying to add a page, I finally had a brand new site I could add a page to! Check out my game dev resources [over here](/gamedev/resources/) as I add more to it!
