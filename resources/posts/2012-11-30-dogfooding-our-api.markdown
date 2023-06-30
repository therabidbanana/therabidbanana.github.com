---
layout: post
title: "Dogfooding Our Api"
date: 2012-11-30 14:33
comments: true
categories:
---

(Crossposted from the reachably blog: http://blog.reachably.com/post/36887084961/dogfooding-our-api [link dead])

Reachably wasn't always Reachably - we used to be called "dtime". This was back when we were building our initial prototype with a focus on making decisions. We learned quite a few lessons there - some business (people didn't like the formality of making a decision publicly online) and some technical (we had a mess of a code base from all of the changes we were making).

Our most important lesson was that it's really hard to evolve an app rapidly without making a mess of the code. One of the messiest parts of the code base was where we started building in JSON api components so that we could start building Backbone components to make our site more dynamic. Building the JSON API parts as needed led to a very haphazard style that wasn't useful at all to external apps. We also ended up taking some shortcuts with our views, allowing too much logic to creep in.

When we started working on the new iteration of Reachably, we decided we were going to throw away our prototype and start from scratch, which gave us the ability make things better the second time around. We started knowing that with our new version we would want to build both mobile and web applications, which meant a solid API would be necessary. Planning wise, we also wanted to prevent ourselves from abusing Rails a second time, if possible, by maintaining a clear separation of concerns. With those thoughts in mind, the decision was made to break our app in half, starting a new project with an API and our web app, which would simply be our first API client, to be followed in time with our mobile client.

Building your own application on top of your non-existent API does require a bit of effort, especially when you're not exactly sure what you want your app to do yet. To help hit the ground running, we started by building out the Rails app with a standard ActiveRecord backend. This helped us work out kinks in the data model and have our frontend developer be able to focus on building and testing the client without waiting for the API to be functional.

To keep the implementation details of persistence away from the client, we used something like the&nbsp;<a href="http://martinfowler.com/eaaCatalog/repository.html">Repository Pattern</a>. We put an interface between the actual models and the controllers this way - rather than directly calling&nbsp;<code>#find</code>&nbsp;on the model, we would call&nbsp;<code>ModelRepository#find</code>, which would then call&nbsp;<code>#find</code>&nbsp;on the ActiveRecord model for us. We also decorated all of our models with Draper, rather than using them directly in the views.

This level of indirection seemed a little contrived while working with just straight ActiveRecord models, but when we started to connect the API, the indirection really paid off - we were able to get the client working with actual API calls in the space of a day. The repository just started making remote calls instead of local calls to the database, then we'd wrap the response in a model that looked very similar to what we'd had with ActiveRecord. With Draper, we were able to patch over differences that had cropped up in the ActiveRecord models that hadn't been migrated over to the API.

We've been working on the app separated this way for a while now and we've found it to be a very effective way to keep a separation of concerns and avoid duplicating work between the mobile and web clients. It's a lot easier to think about new features in terms of the bare minimum of JSON api call + response, rather than worrying about how it will be presented, which can be worked on independently by our front-end developer. There are still a few pain points, the main one being that it's still a little hard to do full stack integration tests, since you have to run a test version of the API and connect to it with the web client.

Lessons Learned
---------------

If you're building a new app and you know you're going to need an API along with the app, I'd highly recommend considering building it the way we did - as two separate, loosely coupled components. Building on your own API calls from the beginning keeps you from taking shortcuts that will make life more difficult when you eventually need to support the same features via your API and forces you to think about how the API will work from the beginning.

* If you build both apps simultaneously - build with enough abstraction to eventually pull out the persistence layer of the client application. (Repositories, Decorators, etc)
* Have an easy way to run your test API server as part of your standard testing framework, it gets painful having to remember to boot up the test server all the time.
* Forced separation of concerns between client and API is a very useful way to help keep your code clean.
