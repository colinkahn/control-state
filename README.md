# control-state

A small library to be used with [Reagent](https://reagent-project.github.io/) to
define control states based on your app state.

## Usage

First, create a control state based on a reagent state atom:

```clojure
(ns my-app.core
    (:require [reagent.core :as r]
              [control-state.core :as c]))

(defonce app-state (r/atom {}))

(defonce ctrl-state (c/ctrl-state app-state))
```

This will give you a `CtrlState`, which implements `IDeref` as well as the
`ISynchronized` protocols. Don't worry about what `ISynchronized` does, the
important part is that you can deref it in your components.

Let's define our first control state predicate. We'll pretend this is an app that
launches a rocket (see [here](http://sam.js.org/) and
[here](https://dzone.com/articles/the-three-approximations-you-should-never-use-when)
for why this example was chosen). We'll give it some initial state as well:

```clojure
(def counter-max 10)

(defonce app-state (r/atom {:count counter-max
                            :started false
                            :launched false
                            :aborted false}))

(defonce ctrl-state (c/ctrl-state app-state))

(c/reg-pred
  :ready
  #(and (= (:count %) counter-max)
        (not (:started %))
        (not (:launched %))
        (not (:aborted %))))
```

Now, when we deref `ctrl-state` it will return `:ready`. We can use this in our
views to enable or disable actions available to the user.

```clojure
(defn view []
  (let [state @app-state
        ctrl @ctrl-state]
    [:div
      (condp = ctrl
        :ready
        [:button "Start Countdown"]

        :counting
        [:h1 (:count state)]
        
        :launched
        [:h1 "Launched!"]
        
        :aborted
        [:h1 "Aborted"]
        
        :else
        "Invalid state")
      ]))
```

Let's define the other control state predicates as well:

```clojure
(c/reg-pred
  :counting
  #(and (<= (:count %) counter-max)
        (:started %)
        (not (:launched %))
        (not (:aborted %))))

(c/reg-pred
  :launched
  #(and (zero? (:count %))
        (:started %)
        (:launched %)
        (not (:aborted %))))

(c/reg-pred
  :aborted
  #(and (<= (:count %) counter-max)
        (not (zero? (:count %)))
        (:started %)
        (not (:launched %))
        (:aborted %)))
```

Let's add some more controls. We'll use these to update our app-state:

```clojure
(defn view []
  (let [state @app-state
        ctrl @ctrl-state]
    [:div
      (condp = ctrl
        :ready
        [:button {:on-click #(swap! app-state assoc :started true)} "Start Countdown"]

        :counting
        [:div
          [:h1 (:count state)]
          [:button {:on-click #(swap! app-state assoc :aborted true)} "Abort"]]
        
        :launched
        [:h1 "Launched!"]
        
        :aborted
        [:h1 "Aborted"]
        
        :else
        "Invalid state")
      ]))
```

Finally, we need something to do the actual countdown. We'll use a next action
predicate (nap) to start the countdown after we enter the counting state:

```clojure
(c/reg-nap
  :counting
  (fn [state]
    (if (zero? (:count state))
      (swap! app-state assoc :launched true)
      (js/setTimeout #(swap! app-state update :count dec) 1000))))
```

## API

### `ctrl-state`

Takes a reagent atom src-atom and returns a CtrlState that when dereference
returns an equivalent control state keyword.

### `reg-pred`

Takes a control state key k and a predicate f and registers them to be checked
against when the app state updates.

### `reg-nap`

Takes a control state key k and a next action predicate f and registers them to
be called when k becomes the current control state.

### `reg-dests`

Takes a control state key k and a set of destination control states s and
registers them to be used to determine a valid next control state from which the
control state k can transition to. If destinations are not defined for a
control state then all registered control state predicates are checked against.

This is mainly here for performance tweaking, and can be ignored otherwise.

### `is-state?`

Takes an app state m and a control state k and checks to see if they are
equivalent.
