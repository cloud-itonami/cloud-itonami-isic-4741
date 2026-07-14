(ns techretail.store
  "SSoT for the computer-retail + trade-in actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/techretail/store_contract_test.clj), which is the whole point:
  the actor, the Retail Governor and the audit ledger never know which
  SSoT they run on.

  UNLIKE `automotive.store` (one entity, two actuations on it), this
  domain has TWO entity types -- an `order` (a customer purchase,
  possibly bundling a trade-in) and a `trade-in-unit` (the traded-in
  device) -- each with its OWN actuation, own append-only history, own
  jurisdiction-scoped sequence counter and own dedicated double-
  actuation-guard boolean (`:order-fulfilled?` on the order /
  `:sanitization-certified?` on the trade-in-unit, never a `:status`
  value) -- the same discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-lifecycle
  bug (ADR-2607071320). The two entities are deliberately NOT cross-
  referenced by the governor: an order's `:trade-in-unit-id` is
  descriptive linkage only (which device this order's credit came
  from), not a dependency either actuation's HARD checks traverse --
  each entity's own governed lifecycle is independent, avoiding
  speculative cross-entity coupling this fleet has not needed
  elsewhere.

  The ledger stays append-only on every backend: 'which trade-in
  device was screened for an unresolved grading defect, which order
  was fulfilled, which Certificate of Data Destruction was issued, on
  what jurisdictional basis, approved by whom' is always a query over
  an immutable log -- the audit trail a community trusting a computer
  retailer's trade-in program needs, and the evidence a retailer needs
  if a fulfillment or data-destruction decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [techretail.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (order [s id])
  (all-orders [s])
  (trade-in-unit [s id])
  (all-trade-in-units [s])
  (consumer-protection-verification-of [s order-id] "committed consumer-protection evidence-checklist verification for an order, or nil")
  (trade-in-condition-screen-of [s trade-in-unit-id] "committed trade-in-condition grading/defect screening verdict, or nil")
  (ledger [s])
  (fulfillment-history [s] "the append-only order-fulfillment history (techretail.registry drafts)")
  (sanitization-certificate-history [s] "the append-only Certificate-of-Data-Destruction history (techretail.registry drafts)")
  (next-fulfillment-sequence [s jurisdiction] "next fulfillment-number sequence for a jurisdiction")
  (next-sanitization-sequence [s jurisdiction] "next sanitization-certificate-number sequence for a jurisdiction")
  (order-already-fulfilled? [s order-id] "has this order already been fulfilled?")
  (trade-in-unit-already-sanitization-certified? [s trade-in-unit-id] "has this device's Certificate of Data Destruction already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-orders [s orders] "replace/seed the order directory (map id->order)")
  (with-trade-in-units [s trade-in-units] "replace/seed the trade-in-unit directory (map id->trade-in-unit)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained order + trade-in-unit set covering both
  actuation lifecycles (fulfilling an order, issuing a Certificate of
  Data Destruction) so the actor + tests run offline."
  []
  {:orders
   {"order-1" {:id "order-1" :customer-name "Kenji Sato"
               :jurisdiction "JPN"
               :items [{:sku "SKU-NB-14" :qty 1 :unit-price 128000}]
               :order-total-actual 128000 :order-total-tolerance 0.01
               :trade-in-unit-id "unit-1"
               :order-fulfilled? false :status :intake}
    "order-2" {:id "order-2" :customer-name "Alicia Ntumba"
               :jurisdiction "ATL"
               :items [{:sku "SKU-MON-27" :qty 2 :unit-price 45000}]
               :order-total-actual 90000 :order-total-tolerance 0.01
               :trade-in-unit-id nil
               :order-fulfilled? false :status :intake}
    "order-3" {:id "order-3" :customer-name "Priya Raman"
               :jurisdiction "JPN"
               :items [{:sku "SKU-NB-14" :qty 1 :unit-price 128000}]
               :order-total-actual 158000 :order-total-tolerance 0.01
               :trade-in-unit-id nil
               :order-fulfilled? false :status :intake}
    "order-4" {:id "order-4" :customer-name "Diego Fuentes"
               :jurisdiction "JPN"
               :items [{:sku "SKU-KB-01" :qty 3 :unit-price 8000}]
               :order-total-actual 24000 :order-total-tolerance 0.01
               :trade-in-unit-id nil
               :order-fulfilled? false :status :intake}}
   :trade-in-units
   {"unit-1" {:id "unit-1" :device-model "Sakura NoteBook Pro 14" :device-serial "SNP14-000123"
              :jurisdiction "JPN"
              :grading-defect-unresolved? false
              :post-wipe-recoverable-sectors-found 0
              :sanitization-sim-verified? false :sanitization-sim-record nil
              :sanitization-certified? false :status :intake}
    "unit-2" {:id "unit-2" :device-model "Atlantis UltraBook Air" :device-serial "AUA-000456"
              :jurisdiction "JPN"
              :grading-defect-unresolved? true
              :post-wipe-recoverable-sectors-found 0
              :sanitization-sim-verified? false :sanitization-sim-record nil
              :sanitization-certified? false :status :intake}
    "unit-3" {:id "unit-3" :device-model "鈴木デスクトップ DT-09" :device-serial "SZK-DT09-000789"
              :jurisdiction "JPN"
              :grading-defect-unresolved? false
              :post-wipe-recoverable-sectors-found 4
              :sanitization-sim-verified? true :sanitization-sim-record nil
              :sanitization-certified? false :status :intake}
    "unit-4" {:id "unit-4" :device-model "田中モニター一体型PC 27型" :device-serial "TNK-AIO27-000234"
              :jurisdiction "JPN"
              :grading-defect-unresolved? false
              :post-wipe-recoverable-sectors-found 0
              :sanitization-sim-verified? false :sanitization-sim-record nil
              :sanitization-certified? false :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- fulfill-order!
  "Backend-agnostic `:order/mark-fulfilled` -- looks up the order via
  the protocol and drafts the order-fulfillment record, and returns
  {:result .. :order-patch ..} for the caller to persist."
  [s order-id]
  (let [o (order s order-id)
        seq-n (next-fulfillment-sequence s (:jurisdiction o))
        result (registry/register-order-fulfillment order-id (:jurisdiction o) seq-n)]
    {:result result
     :order-patch {:order-fulfilled? true
                   :fulfillment-number (get result "fulfillment_number")}}))

(defn- issue-sanitization-certificate!
  "Backend-agnostic `:trade-in-unit/mark-sanitization-certified` --
  looks up the trade-in-unit via the protocol and drafts the
  Certificate-of-Data-Destruction record, and returns {:result ..
  :trade-in-unit-patch ..} for the caller to persist."
  [s trade-in-unit-id]
  (let [u (trade-in-unit s trade-in-unit-id)
        seq-n (next-sanitization-sequence s (:jurisdiction u))
        result (registry/register-sanitization-certificate trade-in-unit-id (:jurisdiction u) seq-n)]
    {:result result
     :trade-in-unit-patch {:sanitization-certified? true
                           :certificate-number (get result "certificate_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (order [_ id] (get-in @a [:orders id]))
  (all-orders [_] (sort-by :id (vals (:orders @a))))
  (trade-in-unit [_ id] (get-in @a [:trade-in-units id]))
  (all-trade-in-units [_] (sort-by :id (vals (:trade-in-units @a))))
  (consumer-protection-verification-of [_ order-id] (get-in @a [:consumer-protection-verifications order-id]))
  (trade-in-condition-screen-of [_ trade-in-unit-id] (get-in @a [:trade-in-condition-screens trade-in-unit-id]))
  (ledger [_] (:ledger @a))
  (fulfillment-history [_] (:fulfillments @a))
  (sanitization-certificate-history [_] (:sanitization-certificates @a))
  (next-fulfillment-sequence [_ jurisdiction] (get-in @a [:fulfillment-sequences jurisdiction] 0))
  (next-sanitization-sequence [_ jurisdiction] (get-in @a [:sanitization-sequences jurisdiction] 0))
  (order-already-fulfilled? [_ order-id] (boolean (get-in @a [:orders order-id :order-fulfilled?])))
  (trade-in-unit-already-sanitization-certified? [_ trade-in-unit-id] (boolean (get-in @a [:trade-in-units trade-in-unit-id :sanitization-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (swap! a update-in [:orders (:id value)] merge value)

      :trade-in-unit/upsert
      (swap! a update-in [:trade-in-units (:id value)] merge value)

      :consumer-protection-verification/set
      (swap! a assoc-in [:consumer-protection-verifications (first path)] payload)

      :trade-in-condition-screen/set
      (swap! a assoc-in [:trade-in-condition-screens (first path)] payload)

      :order/mark-fulfilled
      (let [order-id (first path)
            {:keys [result order-patch]} (fulfill-order! s order-id)
            jurisdiction (:jurisdiction (order s order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:fulfillment-sequences jurisdiction] (fnil inc 0))
                       (update-in [:orders order-id] merge order-patch)
                       (update :fulfillments registry/append result))))
        result)

      :trade-in-unit/mark-sanitization-certified
      (let [trade-in-unit-id (first path)
            {:keys [result trade-in-unit-patch]} (issue-sanitization-certificate! s trade-in-unit-id)
            jurisdiction (:jurisdiction (trade-in-unit s trade-in-unit-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sanitization-sequences jurisdiction] (fnil inc 0))
                       (update-in [:trade-in-units trade-in-unit-id] merge trade-in-unit-patch)
                       (update :sanitization-certificates registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-orders [s orders] (when (seq orders) (swap! a assoc :orders orders)) s)
  (with-trade-in-units [s trade-in-units] (when (seq trade-in-units) (swap! a assoc :trade-in-units trade-in-units)) s))

(defn seed-db
  "A MemStore seeded with the demo order + trade-in-unit set. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :consumer-protection-verifications {} :trade-in-condition-screens {} :ledger []
                           :fulfillment-sequences {} :fulfillments []
                           :sanitization-sequences {} :sanitization-certificates []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/screen payloads, ledger facts,
  fulfillment/certificate records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:order/id                              {:db/unique :db.unique/identity}
   :trade-in-unit/id                      {:db/unique :db.unique/identity}
   :consumer-protection-verification/order-id {:db/unique :db.unique/identity}
   :trade-in-condition-screen/unit-id     {:db/unique :db.unique/identity}
   :ledger/seq                            {:db/unique :db.unique/identity}
   :fulfillment/seq                       {:db/unique :db.unique/identity}
   :sanitization-certificate/seq          {:db/unique :db.unique/identity}
   :fulfillment-sequence/jurisdiction     {:db/unique :db.unique/identity}
   :sanitization-sequence/jurisdiction    {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- order->tx [{:keys [id customer-name jurisdiction items
                           order-total-actual order-total-tolerance
                           trade-in-unit-id order-fulfilled? status fulfillment-number]}]
  (cond-> {:order/id id}
    customer-name                    (assoc :order/customer-name customer-name)
    jurisdiction                     (assoc :order/jurisdiction jurisdiction)
    (some? items)                    (assoc :order/items (enc items))
    order-total-actual               (assoc :order/order-total-actual order-total-actual)
    order-total-tolerance            (assoc :order/order-total-tolerance order-total-tolerance)
    (some? trade-in-unit-id)         (assoc :order/trade-in-unit-id (or trade-in-unit-id ""))
    (some? order-fulfilled?)         (assoc :order/order-fulfilled? order-fulfilled?)
    status                           (assoc :order/status status)
    fulfillment-number               (assoc :order/fulfillment-number fulfillment-number)))

(def ^:private order-pull
  [:order/id :order/customer-name :order/jurisdiction :order/items
   :order/order-total-actual :order/order-total-tolerance :order/trade-in-unit-id
   :order/order-fulfilled? :order/status :order/fulfillment-number])

(defn- pull->order [m]
  (when (:order/id m)
    {:id (:order/id m) :customer-name (:order/customer-name m)
     :jurisdiction (:order/jurisdiction m)
     :items (dec* (:order/items m))
     :order-total-actual (:order/order-total-actual m)
     :order-total-tolerance (:order/order-total-tolerance m)
     :trade-in-unit-id (let [v (:order/trade-in-unit-id m)] (when (and v (not= v "")) v))
     :order-fulfilled? (boolean (:order/order-fulfilled? m))
     :status (:order/status m) :fulfillment-number (:order/fulfillment-number m)}))

(defn- trade-in-unit->tx [{:keys [id device-model device-serial jurisdiction
                                   grading-defect-unresolved? post-wipe-recoverable-sectors-found
                                   sanitization-sim-verified? sanitization-sim-record
                                   sanitization-certified? status certificate-number]}]
  (cond-> {:trade-in-unit/id id}
    device-model                                (assoc :trade-in-unit/device-model device-model)
    device-serial                                (assoc :trade-in-unit/device-serial device-serial)
    jurisdiction                                 (assoc :trade-in-unit/jurisdiction jurisdiction)
    (some? grading-defect-unresolved?)           (assoc :trade-in-unit/grading-defect-unresolved? grading-defect-unresolved?)
    (some? post-wipe-recoverable-sectors-found)  (assoc :trade-in-unit/post-wipe-recoverable-sectors-found post-wipe-recoverable-sectors-found)
    (some? sanitization-sim-verified?)           (assoc :trade-in-unit/sanitization-sim-verified? sanitization-sim-verified?)
    (some? sanitization-sim-record)              (assoc :trade-in-unit/sanitization-sim-record (enc sanitization-sim-record))
    (some? sanitization-certified?)              (assoc :trade-in-unit/sanitization-certified? sanitization-certified?)
    status                                       (assoc :trade-in-unit/status status)
    certificate-number                           (assoc :trade-in-unit/certificate-number certificate-number)))

(def ^:private trade-in-unit-pull
  [:trade-in-unit/id :trade-in-unit/device-model :trade-in-unit/device-serial :trade-in-unit/jurisdiction
   :trade-in-unit/grading-defect-unresolved? :trade-in-unit/post-wipe-recoverable-sectors-found
   :trade-in-unit/sanitization-sim-verified? :trade-in-unit/sanitization-sim-record
   :trade-in-unit/sanitization-certified? :trade-in-unit/status :trade-in-unit/certificate-number])

(defn- pull->trade-in-unit [m]
  (when (:trade-in-unit/id m)
    {:id (:trade-in-unit/id m) :device-model (:trade-in-unit/device-model m) :device-serial (:trade-in-unit/device-serial m)
     :jurisdiction (:trade-in-unit/jurisdiction m)
     :grading-defect-unresolved? (boolean (:trade-in-unit/grading-defect-unresolved? m))
     :post-wipe-recoverable-sectors-found (:trade-in-unit/post-wipe-recoverable-sectors-found m)
     :sanitization-sim-verified? (boolean (:trade-in-unit/sanitization-sim-verified? m))
     :sanitization-sim-record (dec* (:trade-in-unit/sanitization-sim-record m))
     :sanitization-certified? (boolean (:trade-in-unit/sanitization-certified? m))
     :status (:trade-in-unit/status m) :certificate-number (:trade-in-unit/certificate-number m)}))

(defrecord DatomicStore [conn]
  Store
  (order [_ id]
    (pull->order (d/pull (d/db conn) order-pull [:order/id id])))
  (all-orders [_]
    (->> (d/q '[:find [?id ...] :where [?e :order/id ?id]] (d/db conn))
         (map #(pull->order (d/pull (d/db conn) order-pull [:order/id %])))
         (sort-by :id)))
  (trade-in-unit [_ id]
    (pull->trade-in-unit (d/pull (d/db conn) trade-in-unit-pull [:trade-in-unit/id id])))
  (all-trade-in-units [_]
    (->> (d/q '[:find [?id ...] :where [?e :trade-in-unit/id ?id]] (d/db conn))
         (map #(pull->trade-in-unit (d/pull (d/db conn) trade-in-unit-pull [:trade-in-unit/id %])))
         (sort-by :id)))
  (consumer-protection-verification-of [_ order-id]
    (dec* (d/q '[:find ?p . :in $ ?oid
                :where [?a :consumer-protection-verification/order-id ?oid] [?a :consumer-protection-verification/payload ?p]]
              (d/db conn) order-id)))
  (trade-in-condition-screen-of [_ trade-in-unit-id]
    (dec* (d/q '[:find ?p . :in $ ?uid
                :where [?k :trade-in-condition-screen/unit-id ?uid] [?k :trade-in-condition-screen/payload ?p]]
              (d/db conn) trade-in-unit-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (fulfillment-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :fulfillment/seq ?s] [?e :fulfillment/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (sanitization-certificate-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :sanitization-certificate/seq ?s] [?e :sanitization-certificate/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-fulfillment-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :fulfillment-sequence/jurisdiction ?j] [?e :fulfillment-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-sanitization-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :sanitization-sequence/jurisdiction ?j] [?e :sanitization-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (order-already-fulfilled? [s order-id]
    (boolean (:order-fulfilled? (order s order-id))))
  (trade-in-unit-already-sanitization-certified? [s trade-in-unit-id]
    (boolean (:sanitization-certified? (trade-in-unit s trade-in-unit-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (d/transact! conn [(order->tx value)])

      :trade-in-unit/upsert
      (d/transact! conn [(trade-in-unit->tx value)])

      :consumer-protection-verification/set
      (d/transact! conn [{:consumer-protection-verification/order-id (first path) :consumer-protection-verification/payload (enc payload)}])

      :trade-in-condition-screen/set
      (d/transact! conn [{:trade-in-condition-screen/unit-id (first path) :trade-in-condition-screen/payload (enc payload)}])

      :order/mark-fulfilled
      (let [order-id (first path)
            {:keys [result order-patch]} (fulfill-order! s order-id)
            jurisdiction (:jurisdiction (order s order-id))
            next-n (inc (next-fulfillment-sequence s jurisdiction))]
        (d/transact! conn
                     [(order->tx (assoc order-patch :id order-id))
                      {:fulfillment-sequence/jurisdiction jurisdiction :fulfillment-sequence/next next-n}
                      {:fulfillment/seq (count (fulfillment-history s)) :fulfillment/record (enc (get result "record"))}])
        result)

      :trade-in-unit/mark-sanitization-certified
      (let [trade-in-unit-id (first path)
            {:keys [result trade-in-unit-patch]} (issue-sanitization-certificate! s trade-in-unit-id)
            jurisdiction (:jurisdiction (trade-in-unit s trade-in-unit-id))
            next-n (inc (next-sanitization-sequence s jurisdiction))]
        (d/transact! conn
                     [(trade-in-unit->tx (assoc trade-in-unit-patch :id trade-in-unit-id))
                      {:sanitization-sequence/jurisdiction jurisdiction :sanitization-sequence/next next-n}
                      {:sanitization-certificate/seq (count (sanitization-certificate-history s)) :sanitization-certificate/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-orders [s orders]
    (when (seq orders) (d/transact! conn (mapv order->tx (vals orders)))) s)
  (with-trade-in-units [s trade-in-units]
    (when (seq trade-in-units) (d/transact! conn (mapv trade-in-unit->tx (vals trade-in-units)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:orders .. :trade-in-units ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [orders trade-in-units]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-orders s orders)
     (with-trade-in-units s trade-in-units))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo order + trade-in-unit set -- the
  Datomic-backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
