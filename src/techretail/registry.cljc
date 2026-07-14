(ns techretail.registry
  "Pure-function order-fulfillment + Certificate-of-Data-Destruction
  record construction -- an append-only computer-retailer book-of-
  record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for an order-fulfillment or
  Certificate-of-Data-Destruction reference number -- every retailer/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `techretail.facts` uses --
  `automotive.registry`'s ns docstring (`cloud-itonami-isic-2910`)
  established this discipline first.

  `order-total-mismatch?` is this actor's own order-side ground-truth
  range/consistency check, in the spirit of this fleet's two-sided
  range-check family (`automotive.registry/vehicle-emissions-out-of-
  range?` and its siblings -- see that ns's docstring for the lineage):
  a pure recompute of an order's own recorded line-items against its
  own recorded total, no upstream comparison needed. The domain's
  MANDATORY ground-truth check -- whether a trade-in device's own
  post-wipe verification read confirms zero recoverable sectors -- is
  `techretail.robotics/sanitization-incomplete?`, not here (it needs no
  arithmetic recompute over a list, so it stays with the robotics
  mission that produces the verification read, matching
  `automotive.robotics/simulation-out-of-tolerance?`'s placement).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real POS/shipping/ITAD system. It builds the RECORD a
  retailer would keep, not the act of shipping the purchased device or
  issuing the Certificate of Data Destruction itself (that is
  `techretail.operation`'s `:actuation/fulfill-order`/`:actuation/
  issue-sanitization-certificate`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  retailer's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn order-total-mismatch?
  "Does `order`'s own recorded `:order-total-actual` differ from the
  sum of its own recorded `:items` (`:qty` * `:unit-price`) by more
  than its own recorded `:order-total-tolerance` (default 0.01)? A pure
  ground-truth check against the order's own permanent fields -- no
  upstream comparison needed. This fleet's two-sided range-check
  family, applied here to an order's own arithmetic instead of a
  measured physical deviation."
  [{:keys [order-total-actual items order-total-tolerance]
    :or {order-total-tolerance 0.01}}]
  (when (and (number? order-total-actual) (sequential? items) (seq items)
             (every? #(and (number? (:qty %)) (number? (:unit-price %))) items))
    (let [computed (reduce + 0 (map #(* (:qty %) (:unit-price %)) items))
          delta (- order-total-actual computed)]
      (> (if (neg? delta) (- delta) delta) order-total-tolerance))))

(defn register-order-fulfillment
  "Validate + construct the ORDER-FULFILLMENT registration DRAFT -- the
  retailer's own act of shipping a purchased device to a customer.
  Pure function -- does not touch any real POS/shipping/carrier
  system; it builds the RECORD a retailer would keep.
  `techretail.governor` independently re-verifies the order's own
  arithmetic sufficiency and evidence completeness, and a double-
  fulfillment for the same order, before this is ever allowed to
  commit."
  [order-id jurisdiction sequence]
  (when-not (and order-id (not= order-id ""))
    (throw (ex-info "order-fulfillment: order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "order-fulfillment: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "order-fulfillment: sequence must be >= 0" {})))
  (let [fulfillment-number (str (str/upper-case jurisdiction) "-FUL-" (zero-pad sequence 6))
        record {"record_id" fulfillment-number
                "kind" "order-fulfillment-draft"
                "order_id" order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "fulfillment_number" fulfillment-number
     "certificate" (unsigned-certificate "OrderFulfillment" fulfillment-number fulfillment-number)}))

(defn register-sanitization-certificate
  "Validate + construct the CERTIFICATE-OF-DATA-DESTRUCTION
  registration DRAFT -- the retailer's own act of issuing a real
  Certificate of Data Destruction certifying a trade-in device as
  sanitized per NIST SP 800-88 Rev. 2 (`techretail.facts/sanitization-
  standard`). Pure function -- does not touch any real ITAD/sanitization
  rig; it builds the RECORD a retailer would keep.
  `techretail.governor` independently re-verifies the trade-in device's
  own post-wipe verification-read completeness, and a double-issuance
  for the same device, before this is ever allowed to commit."
  [trade-in-unit-id jurisdiction sequence]
  (when-not (and trade-in-unit-id (not= trade-in-unit-id ""))
    (throw (ex-info "sanitization-certificate: trade_in_unit_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "sanitization-certificate: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "sanitization-certificate: sequence must be >= 0" {})))
  (let [certificate-number (str (str/upper-case jurisdiction) "-COD-" (zero-pad sequence 6))
        record {"record_id" certificate-number
                "kind" "sanitization-certificate-draft"
                "trade_in_unit_id" trade-in-unit-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "certificate_number" certificate-number
     "certificate" (unsigned-certificate "CertificateOfDataDestruction" certificate-number certificate-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
