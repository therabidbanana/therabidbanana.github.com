---
layout: post
title: "How We Test our Full-stack Clojure App"
date: 2017-07-08 20:50
comments: true
categories:
---


*Note: Reposted from the [AdStage Engineering Blog](https://medium.com/adstage-engineering/how-we-test-our-full-stack-clojure-app-b18d79ee9e00)*

[AdStage Report](https://www.adstage.io/reporting/) is a reporting product for advertisers built in [Untangled](http://untangled-web.github.io/untangled/) and powered by the AdStage Platform API. It is still being actively developed, so code is being committed daily and we need to be sure we aren’t accidentally breaking features we’ve already shipped without a lot of tedious manual QA.

In our previous work with Ruby on Rails and Ember.js projects, we’ve relied heavily on testing to keep our quality up, so when we made the decision to experiment with a full-stack Clojure application in Report, one of the things we were worried about was how we’d be able to replace those tried and true testing solutions we had built up in an entirely foreign stack.

In some ways, a full-stack Clojure application feels like the wild west of web development and there don’t seem to be a lot of established best practices and pre-built solutions. Luckily, Untangled comes with spec support out of the box to help you get started and we’ve been able to build up a nice set of additional tools without too much effort.

## Manual Testing

The idea of doing manual testing is usually the first thing that comes to mind to any developer — try a change and see if it works. Coming from Ruby to Clojure, there are two special tools that have proven to be especially helpful for making and testing changes –Devcards for testing changes to components, and an integrated REPL for testing bits of server code.

[Devcards](http://rigsomelight.com/devcards/#!/devdemos.core) have been a great help in creating components quickly without having to worry about what the backend code will look like — and with boot-reload (or figwheel), changes you make and save in an editor will instantly load into the browser (no refresh required!), or if you made a mistake, you get a helpful message telling you why the code couldn’t compile.

Because they help encourage isolation for our components, Devcards are great for easily simulating the different states our components can be in. That isolation makes it really easy for us to set up Devcards for failure states just by passing the bad data that causes the failure into a new card. While this is great for development and manual QA, Devcards also come into play later in our automated test suite.

![A typo shows up in Devcards](/assets/clojure-test/blog-1.png)*A typo shows up in Devcards*

Along with Devcards and live reloading components, testing functions in the backend with a REPL has been extremely helpful. We all use CIDER with Spacemacs for development. Quickly testing new functions without having to integrate them into the rest of the code base is almost like unit testing on the fly. You can verify a call to a function with the arguments you expect will give an expected return value with a simple keyboard shortcut before you integrate that function into the rest of the codebase. We take this even further and have a special namespace with some helpers built up specifically to play with different functions and test them in a playground (an idea we took from Stuart Sierra’s [reloaded workflow](http://thinkrelevance.com/blog/2013/06/04/clojure-workflow-reloaded)) before moving them to their final location — something we’ve found comes much more naturally in Clojure than in Ruby.

![Evaluating code in the Spacemacs](/assets/clojure-test/blog-2.gif)*Evaluating code in the Spacemacs*

## Unit Testing

Unit testing in Clojure is pretty straightforward — we use [untangled-spec](https://github.com/untangled-web/untangled-spec) to provide some useful additions to the base clojure.test functionality, and then we run our tests via [boot-test](https://github.com/adzerk-oss/boot-test). We still try to test most of our functions, but in contrast to our experience with Ruby, we haven’t felt the need to obsess over coverage. In general, we’ve found that the combination of a compiler, functional language, and immutable data structures (in addition to a REPL to quickly test edge cases) make a lot of the kinds of tests we’d write in RSpec unnecessary and we can spend more effort on the higher level tests.

A great feature that comes with untangled-spec for client-side unit tests is the spec runner. When combined with live code reloading, it makes it really easy to write and fix unit tests — just keep a browser window open with the test runner and keep saving changes to your code until the tests pass.

![Auto-refreshing Tests](/assets/clojure-test/blog-3.gif)*Auto-refreshing Tests*

## Visual Regression Tests

As already mentioned, Devcards have proven to be a great tool for setting up a playground of components with their various potential states, so why not use them for more than just manual testing? Historically, testing frontend components has been painful for us to do effectively, but with Devcards we stumbled upon a remarkably easy but effective solution: just screenshot all the Devcards we are already using for our development purposes and diff those against known good states. This doesn’t cover truly interactive components, but since most of our application focuses on rendering existing data to the page, we can provide stubs and make sure all the forms and various widget types render correctly when provided correct data.

The setup is pretty simple, we use [clj-webdriver](https://github.com/semperos/clj-webdriver/wiki/Introduction%3A-Taxi) to navigate to our Devcards page, and then have it take a bunch of screenshots. Then we take that list of screenshots and compare to the expected values with [aShot](https://github.com/yandex-qatools/ashot). When we get a failure we can see it like this:

![Visual Regression Detected](/assets/clojure-test/blog-4.png)*Visual Regression Detected*

In this case, our test caught that some “x”s were missing from a component — highlighted in red. The basic code we use to get this to work is in this [gist on Github](https://gist.github.com/adstage-david/864539c452f9fa54851bab09a40f09fb).

The return on investment for this kind of testing has been huge — with a day or so of setup (and some admittedly annoying maintenance of the known good states whenever we introduce intentional changes to the UI), we’ve caught a decent amount of bugs that we otherwise could have easily missed and even ones that previously would have been impossible for us to catch. With this test suite in place we caught a subtle change in spacing due to the combination of a bad style and a React upgrade (the React upgrade removed a wrapper html element our styles were accidentally relying on). This is something that we never would have noticed as developers, but would have been pretty annoying for our designers to have to catch in production.

One thing we definitely learned the hard way is that since we use advanced optimizations for our clojurescript build in production, we need to make sure to use it for our visual regression test build. A few times we ended up pushing code that we never tried with advanced compilation, and having advanced compilations in our visual regression build saved us because the Devcards would end up blank or seriously broken.

## Integration Tests

We have a decent amount of integration tests that run along with our unit tests. These tests verify that various mutations we can call on the server are working as expected. Untangled’s Datomic test helpers (which add some conveniences to set up fixtures), and using the [component library](https://github.com/stuartsierra/component) both have helped a lot in setting up a good environment for a given integration test without having boot up the entire app. We can call our mutations with just the components the mutation needs to run (generally just the database, but sometime our job schedulers or other components).

Since Untangled uses the component library as a simple dependency injection tool, it makes it very easy to use stub our system components where necessary. We’ve used this to create mock job schedulers for cases where we want to test our job scheduling without having to set up Quartz in testing.

## Acceptance Tests

The last part of our testing stack is a set of acceptance tests that boots up the entire app with production-like settings and then tries to log in and make some actual reports. These tests were pretty hard to get properly set up, but unlike the rest of the tests, they’re the only ones that can verify our entire application by running through it as a user, end-to-end. The [Taxi API](https://github.com/semperos/clj-webdriver/wiki/Taxi-API-Documentation) provided by clj-webdriver has been a great help for simulating a user in the browser and we have found a few patterns to help keep the code from getting too messy.

One pattern we use a lot is to use special html attributes for elements we’re interested in testing, since CSS selectors can be subject to change for design purposes. We use the “data-test” attribute, since HTML5 lets us add any arbitrary “data-\*” attributes to tags, and it clearly defines what the element is needed for in the test. Combined with this, we use a set of fairly simple page helpers and a map of CSS selectors to actual element names to end up with tests that look [like this](https://gist.github.com/adstage-david/d83859b62ca09d91e6347de921bfd623).

We run this along with our unit and integration tests — so our smoke test is executed by the clojure.test runner and it makes assertions about the different pages it’s able to click around to.

## Putting it All Together

For Report, we use [boot](http://boot-clj.com/) to build and run our application. Boot offers a lot of flexibility in composing tasks, which made it easy to add new types of test suites as new tasks and reuse our shared system setup/compilation task between those tasks. The composability also came into play when we decided we wanted to combine our test tasks into a single test-all command: by combining the tasks together and sharing the system setup/compilation task, we managed to cut down our CI build times significantly.

## What We’ve Learned

While Clojure’s lack of out-of-the-box tools for specific kinds of testing like we have in Ruby land (Cucumber and RSpec) seemed like a limitation going into this project, it has actually turned out to not be a big deal. The lack of fancy off-the-shelf tools even led us to come up with some interesting solutions like our visual regression suite that we wouldn’t have thought to try otherwise. One of the great things about Clojure is how easy it has been to build our own solutions without a lot of effort from the solid building blocks that are available (like clj-webdriver, Devcards and clojure.test).

If you’re looking to talk more about full-stack Clojure development, we’re often around on the Clojurians slack #untangled channel and willing to chat about our experience, and if you’re interested in doing Clojure development, we’re looking for developers to [join our team](https://angel.co/adstage/jobs?utm_source=eng-blog).
