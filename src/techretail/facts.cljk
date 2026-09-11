(ns techretail.facts
  "Per-jurisdiction consumer-protection / distance-selling evidence
  catalog -- the G4741-style spec-basis table the Retail Governor
  checks every `:consumer-protection-rules/verify` proposal against --
  and the media-sanitization standard basis the Retail Governor's
  data-wipe checks cite.

  Coverage is reported HONESTLY: a jurisdiction not in `catalog` has NO
  spec-basis. Seed values cite official consumer-protection regulators
  and REAL, currently-in-force instruments only; this is a starting
  catalog (JPN/USA/EU), not a survey of every market -- the same
  discipline `automotive.facts` (`cloud-itonami-isic-2910`) established
  for type-approval spec-basis. GBR and other jurisdictions are
  deliberately NOT seeded here; see `coverage`.

  NOTE ON JAPAN: 特定商取引法 (the Act on Specified Commercial
  Transactions) does NOT grant a statutory cooling-off right for
  ordinary 通信販売 (mail-order / distance-selling) purchases -- cooling-
  off in Japanese consumer law is a door-to-door/telemarketing-sales
  concept (訪問販売等). What 通信販売 DOES require is an advertised
  seller-identity/price/payment/delivery disclosure (第11条) and,
  absent a stated return policy, a statutory 8-day return right at the
  buyer's own cost (第15条の3). This catalog cites exactly that --
  it does NOT fabricate a cooling-off right that Japanese mail-order
  law does not grant.")

(def catalog
  {"JPN" {:name "Japan"
          :owner-authority "消費者庁 (Consumer Affairs Agency, CAA)"
          :legal-basis "特定商取引に関する法律 (昭和51年法律第57号) 第11条 (通信販売の広告表示事項) / 第15条の3 (返品特約の表示・法定返品権)"
          :national-spec "通信販売事業者の広告表示義務および返品特約表示・法定返品権"
          :provenance "https://www.no-trouble.caa.go.jp/what/mailorder/"
          :required-evidence ["事業者情報表示 (seller-identity-and-contact-disclosure)"
                              "価格・支払時期・引渡時期の表示 (price-payment-timing-delivery-timing-disclosure)"
                              "返品特約の表示 (return-policy-disclosure, 特定商取引法第15条の3)"]}
   "USA" {:name "United States"
          :owner-authority "FTC (Federal Trade Commission)"
          :legal-basis "16 CFR Part 435 (Mail, Internet, or Telephone Order Merchandise Rule)"
          :national-spec "US mail/internet/telephone order shipment-timing and refund disclosure requirements"
          :provenance "https://www.ftc.gov/legal-library/browse/rules/mail-internet-or-telephone-order-merchandise-rule"
          :required-evidence ["shipment-timing-disclosure (stated time, or 30-day default)"
                              "delay-notice-and-cancellation-right"
                              "refund-timeliness-record"]}
   "EUR" {:name "European Union"
          :owner-authority "European Commission / EU member-state consumer authorities"
          :legal-basis "Directive 2011/83/EU on consumer rights, Article 6 (pre-contract information) and Articles 9-14 (right of withdrawal)"
          :national-spec "EU distance-contract pre-contract-information and 14-day right-of-withdrawal requirements"
          :provenance "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32011L0083"
          :required-evidence ["pre-contract-information-disclosure (Article 6)"
                              "right-of-withdrawal-notice-14-day (Article 9)"
                              "durable-medium-order-confirmation (Article 8(7))"]}})

(defn spec-basis [iso3] (get catalog iso3))

(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4741 R0: " (count catalog)
                 " jurisdictions seeded (JPN/USA/EUR). GBR and others are NOT"
                 " covered -- extend `techretail.facts/catalog`, never"
                 " fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

;; ----------------------------- media-sanitization basis -----------------------------

(def sanitization-standard
  "The real, currently-in-force federal media-sanitization standard the
  `:robotics/simulate-data-wipe` mission and the Retail Governor's
  data-wipe checks cite as their evidence basis for a trade-in device's
  Certificate of Data Destruction. NIST SP 800-88 Rev. 1 (2014-12-17)
  was WITHDRAWN 2025-09-26 and superseded by Rev. 2 (2025-09) -- this
  catalog cites the CURRENT standard, not a withdrawn one, the same
  honesty discipline `catalog` above applies to jurisdiction spec-
  basis. Rev. 2 still defines the same three sanitization categories
  (Clear / Purge / Destroy) and requires a post-sanitization
  VERIFICATION step, which is exactly what `techretail.robotics`'s
  `:post-wipe-functional-test` mission step and
  `sanitization-incomplete?` ground-truth recheck implement."
  {:name "Guidelines for Media Sanitization"
   :owner-authority "NIST (National Institute of Standards and Technology)"
   :legal-basis "NIST SP 800-88 Rev. 2 (2025-09), supersedes SP 800-88 Rev. 1 (2014-12-17, withdrawn 2025-09-26)"
   :national-spec "media-sanitization categories (Clear/Purge/Destroy) + mandatory post-sanitization verification"
   :provenance "https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-88r2.pdf"
   :required-evidence ["sanitization-method-record (Clear|Purge|Destroy)"
                       "post-sanitization-verification-read (recoverable-sector count)"
                       "certificate-of-data-destruction-draft"]})

(defn sanitization-basis [] sanitization-standard)
