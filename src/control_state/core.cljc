(ns control-state.core
  (:require [reagent.core :as r]))

(def state->pred (atom {}))
(def state->nap (atom {}))
(def state->dests (atom {}))

(defn get-state [m state->pred]
  (some #(let [[k f] %] (when (f m) k)) state->pred))

(defn is-state?
  "Takes an app state m and a control state k and checks to see if they are
  equivalent."
  [m k]
  (when-let [f (k @state->pred)]
    (f m)))

(defprotocol ISynchronized
  (-should-sync! [this])
  (-sync! [this]))

(defn select-key-or-children [m k]
 (cond
   (contains? m k)
   {k (k m)}

   :else
   (into {} (filter #(isa? (key %) k) m))))

(defn select-key-or-parents [m k]
  (into {} (filter #(isa? k (key %)) m)))

(deftype CtrlState [src-atom proxy-atom ^:mutable render-fn ^:mutable sync?]
  ISynchronized
  (-should-sync! [this]
    (set! sync? true))

  (-sync! [this]
    (let [k (get-state @src-atom
                       (if-let [dests (get @state->dests @proxy-atom)]
                         (reduce (fn [m k] (into m (select-key-or-children @state->pred k))) {} dests)
                         @state->pred))]
        (reset! proxy-atom k)

        (when-let [naps (-> (select-key-or-parents @state->nap k) vals seq)]
          (let [state @src-atom]
            (set! render-fn #(doseq [nap naps] (nap state)))))))

  IDeref
  (-deref [this]
    (when sync?
      (set! sync? false)
      (-sync! this))

    (when-let [nap render-fn]
      (set! render-fn nil)
      (nap))

    @proxy-atom))

(defn ctrl-state
  "Takes a reagent atom src-atom and returns a CtrlState that when dereference
  returns an equivalent control state keyword."
  [src-atom]
  (let [ctrl (->CtrlState src-atom (r/atom nil) nil true)]
    (add-watch src-atom :sync-state (fn [_ _ _ _] (-should-sync! ctrl)))
    ctrl))

(defn reg-pred
  "Takes a control state key k and a predicate f and registers them to be
  checked against when the app state updates."
  [k f]
  (swap! state->pred assoc k f))

(defn reg-nap
  "Takes a control state key k and a next action predicate f and registers them
  to be called when k becomes the current control state."
  [k f]
  (swap! state->nap assoc k f))

(defn reg-dests
  "Takes a control state key k and a set of destintation control states s and
  registers them to be used to determine a valid next control state from which
  the control state k can transition to. If destinations are not defined for a
  control state then all registered control state predicates are checked against.

  This is mainly here for performance tweaking, and can be ignored otherwise."
  [k s]
  (swap! state->dests assoc k s))
