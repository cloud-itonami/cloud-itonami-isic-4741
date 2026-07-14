(ns techretail.export
  "Audit-package export for social / regulatory hand-off.

  Produces plain EDN maps and CSV strings over a `techretail.store/Store`
  snapshot -- the same append-only ledger, order-fulfillment drafts and
  Certificate-of-Data-Destruction drafts the governor writes. Pure data
  transforms only: no I/O, no network, no signature. The retailer's own
  act is to sign and file the package; this namespace only materializes
  the package body.

  This is the honest delivery of the industry-stack `:export?` contract
  (robotics / audit-ledger capabilities) for ISIC 4741."
  (:require [clojure.string :as str]
            [techretail.store :as store]))

(defn- csv-escape [v]
  (let [s (str (if (nil? v) "" v))]
    (if (re-find #"[,\"\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [cols]
  (str/join "," (map csv-escape cols)))

(defn ledger-rows
  "Normalize ledger facts into flat row maps suitable for CSV."
  [st]
  (mapv (fn [i f]
          {:seq i
           :t (:t f)
           :op (str (:op f))
           :actor (:actor f)
           :subject (:subject f)
           :disposition (str (:disposition f))
           :basis (pr-str (:basis f))
           :summary (:summary f)})
        (range)
        (store/ledger st)))

(defn fulfillment-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :order_id (get r "order_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/fulfillment-history st)))

(defn sanitization-certificate-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :trade_in_unit_id (get r "trade_in_unit_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/sanitization-certificate-history st)))

(defn orders-snapshot [st]
  (mapv (fn [o]
          (select-keys o [:id :customer-name :jurisdiction :status
                          :order-total-actual
                          :trade-in-unit-id
                          :order-fulfilled?
                          :fulfillment-number]))
        (store/all-orders st)))

(defn trade-in-units-snapshot [st]
  (mapv (fn [u]
          (select-keys u [:id :device-model :jurisdiction :status
                          :grading-defect-unresolved?
                          :sanitization-certified?
                          :certificate-number]))
        (store/all-trade-in-units st)))

(defn audit-package
  "Full audit package for a store snapshot -- the body a computer
  retailer would hand to consumer-protection inspectors, e-waste/ITAD
  regulator inspectors or internal compliance. `:format` is always
  `:edn-maps` for the nested package; use `package->csv-bundle` for
  CSV strings."
  [st]
  {:isic "4741"
   :business-id "cloud-itonami-isic-4741"
   :format :edn-maps
   :orders (orders-snapshot st)
   :trade-in-units (trade-in-units-snapshot st)
   :ledger (vec (store/ledger st))
   :fulfillments (vec (store/fulfillment-history st))
   :sanitization-certificates (vec (store/sanitization-certificate-history st))
   :counts {:orders (count (store/all-orders st))
            :trade-in-units (count (store/all-trade-in-units st))
            :ledger (count (store/ledger st))
            :fulfillments (count (store/fulfillment-history st))
            :sanitization-certificates (count (store/sanitization-certificate-history st))}})

(defn rows->csv
  "Render a seq of flat maps as CSV using `header` column order."
  [header rows]
  (let [lines (into [(csv-row (map name header))]
                    (map (fn [r] (csv-row (map #(get r %) header))) rows))]
    (str (str/join "\n" lines) (when (seq lines) "\n"))))

(defn package->csv-bundle
  "CSV bundle for spreadsheet hand-off. Keys are filenames; values are
  CSV body strings."
  [st]
  {"orders.csv" (rows->csv [:id :customer-name :jurisdiction :status
                           :order-total-actual :trade-in-unit-id
                           :order-fulfilled? :fulfillment-number]
                          (orders-snapshot st))
   "trade-in-units.csv" (rows->csv [:id :device-model :jurisdiction :status
                                    :grading-defect-unresolved?
                                    :sanitization-certified? :certificate-number]
                                   (trade-in-units-snapshot st))
   "ledger.csv" (rows->csv [:seq :t :op :actor :subject :disposition :basis :summary]
                           (ledger-rows st))
   "fulfillments.csv" (rows->csv [:seq :record_id :kind :order_id :jurisdiction]
                                 (fulfillment-rows st))
   "sanitization-certificates.csv" (rows->csv [:seq :record_id :kind :trade_in_unit_id :jurisdiction]
                                              (sanitization-certificate-rows st))})

#?(:clj
(defn write-csv-bundle!
  "Write `package->csv-bundle` files under `dir` (created if missing).
  Returns the absolute path of `dir`. JVM-only I/O seam for social
  hand-off scripts; pure package construction stays in `package->csv-bundle`."
  [st dir]
  (let [d (java.io.File. (str dir))
        _ (.mkdirs d)
        bundle (package->csv-bundle st)]
    (doseq [[name body] bundle]
      (spit (java.io.File. d (str name)) body))
    (.getAbsolutePath d))))
