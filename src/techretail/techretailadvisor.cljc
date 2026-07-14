(ns techretail.techretailadvisor
  "Retail Advisor client -- the *contained intelligence node* for the
  computer-retail + trade-in actor.

  It normalizes order-intake, drafts a per-jurisdiction consumer-
  protection / distance-selling evidence checklist for an order,
  screens trade-in devices for an unresolved grading/defect finding,
  drafts the robot certified-data-wipe mission result, drafts the robot
  functional drop/shock-test mission result (ADR-2607152000, a REAL
  `physics-2d`-simulated free-fall/impact check), drafts the
  order-fulfillment action, and drafts the Certificate-of-Data-
  Destruction-issuance action. CRITICAL: it is a smart-but-untrusted
  advisor. It returns a *proposal* (with a rationale + the fields it
  cited), never a committed record or a real shipment/Certificate-of-
  Data-Destruction issuance. Every output is censored downstream by
  `techretail.governor` before anything touches the SSoT, and
  `:actuation/fulfill-order`/`:actuation/issue-sanitization-
  certificate` proposals NEVER auto-commit at any phase -- see README
  `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/fulfill-order | :actuation/issue-sanitization-certificate | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [techretail.facts :as facts]
            [techretail.registry :as registry]
            [techretail.robotics :as robotics]
            [techretail.store :as store]
            [langchain.model :as model]))

(defn- normalize-order-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the order, its line-items or its jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "注文記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :order/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-consumer-protection
  "Per-jurisdiction consumer-protection/distance-selling evidence
  checklist draft. `:no-spec?` injects the failure mode we must defend
  against: proposing a checklist for a jurisdiction with NO official
  spec-basis in `techretail.facts` -- the Retail Governor must reject
  this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [o (store/order db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction o))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "techretail.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :consumer-protection-verification/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要開示事項 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :consumer-protection-verification/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-trade-in-condition
  "Trade-in grading/defect screening draft.
  `:grading-defect-unresolved?` on the trade-in-unit record injects the
  failure mode: the Retail Governor must HOLD, un-overridably, on any
  unresolved grading/defect finding."
  [db {:keys [subject]}]
  (let [u (store/trade-in-unit db subject)]
    (cond
      (nil? u)
      {:summary "対象トレードイン機記録が見つかりません" :rationale "no trade-in-unit record"
       :cites [] :effect :trade-in-condition-screen/set :value {:trade-in-unit-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:grading-defect-unresolved? u))
      {:summary    (str (:device-model u) ": 未解決のグレーディング欠陥を検出")
       :rationale  "トレードイン機検品が未解決の欠陥(破損/機能不良)を検出。人手確認とホールドが必須。"
       :cites      [:grading-check]
       :effect     :trade-in-condition-screen/set
       :value      {:trade-in-unit-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:device-model u) ": 未解決のグレーディング欠陥なし")
       :rationale  "トレードイン機検品完了。"
       :cites      [:grading-check]
       :effect     :trade-in-condition-screen/set
       :value      {:trade-in-unit-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- simulate-data-wipe
  "Runs the robot certified data-wipe mission (`techretail.robotics`)
  and drafts its result as a proposal. High confidence -- the mission
  itself is deterministic simulated telemetry derived from the trade-
  in-unit's own recorded post-wipe verification-read field, not an LLM
  guess; the Retail Governor still independently re-derives :passed?
  from that same field before any `:actuation/issue-sanitization-
  certificate` proposal may commit -- see `techretail.governor`'s
  `data-wipe-mission-violations`."
  [db {:keys [subject]}]
  (let [u (store/trade-in-unit db subject)]
    (if (nil? u)
      {:summary "対象トレードイン機記録が見つかりません" :rationale "no trade-in-unit record"
       :cites [] :effect :trade-in-unit/upsert :value {:id subject :sanitization-sim-verified? false}
       :stake nil :confidence 0.0}
      (let [{:keys [mission actions passed?]} (robotics/simulate-data-wipe subject u)]
        {:summary    (str subject ": 認定データ消去ミッション " (if passed? "合格" "不合格"))
         :rationale  (str "mission=" (:mission/id mission) " actions=" (count actions)
                          " post-wipe-recoverable-sectors-found=" (:post-wipe-recoverable-sectors-found u))
         :cites      [(:mission/id mission)]
         :effect     :trade-in-unit/upsert
         :value      {:id subject
                      :sanitization-sim-verified? passed?
                      :sanitization-sim-record {:mission-id (:mission/id mission)
                                                :actions (mapv #(dissoc % :action) actions)
                                                :passed? passed?}}
         :stake      nil
         :confidence 0.95}))))

(defn- simulate-drop-test
  "Runs the robot functional drop/shock-test mission
  (`techretail.robotics`, ADR-2607152000) and drafts its result as a
  proposal. This ACTUALLY runs `physics-2d`'s real time-stepped
  free-fall/impact simulation (see `techretail.robotics/simulate-drop-
  test`'s docstring) from the trade-in-unit's own recorded
  `:device-class`/`:device-mass-kg` fields. High confidence -- the
  mission itself is deterministic simulated telemetry derived from a
  REAL physics simulation, not an LLM guess; the Retail Governor still
  independently re-derives :passed? from the REAL `:sim-impact-decel-g`
  telemetry this drafts before any `:actuation/issue-sanitization-
  certificate` proposal may commit -- see `techretail.governor`'s
  `drop-test-violations`."
  [db {:keys [subject]}]
  (let [u (store/trade-in-unit db subject)]
    (if (nil? u)
      {:summary "対象トレードイン機記録が見つかりません" :rationale "no trade-in-unit record"
       :cites [] :effect :trade-in-unit/upsert :value {:id subject :drop-test-sim-verified? false}
       :stake nil :confidence 0.0}
      (let [{:keys [mission actions passed? sim-impact-decel-g sim-impact-penetration-m]}
            (robotics/simulate-drop-test subject u)]
        {:summary    (str subject ": 機能落下/衝撃試験 " (if passed? "合格" "不合格"))
         :rationale  (str "mission=" (:mission/id mission) " actions=" (count actions)
                          " sim-impact-decel-g=" sim-impact-decel-g
                          " sim-impact-penetration-m=" sim-impact-penetration-m)
         :cites      [(:mission/id mission)]
         :effect     :trade-in-unit/upsert
         :value      {:id subject
                      :drop-test-sim-verified? passed?
                      :sim-impact-decel-g sim-impact-decel-g
                      :sim-impact-penetration-m sim-impact-penetration-m
                      :drop-test-sim-record {:mission-id (:mission/id mission)
                                             :actions (mapv #(dissoc % :action) actions)
                                             :passed? passed?}}
         :stake      nil
         :confidence 0.95}))))

(defn- propose-fulfill-order
  "Draft the actual ORDER-FULFILLMENT action -- shipping a real
  purchased device to a customer. ALWAYS `:stake :actuation/fulfill-
  order` -- this is a REAL-WORLD act, never a draft the actor may
  auto-run. See README `Actuation`: no phase ever adds this op to a
  phase's `:auto` set (`techretail.phase`); the governor also always
  escalates on `:actuation/fulfill-order`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [o (store/order db subject)]
    {:summary    (str subject " 向け注文発送提案"
                      (when o (str " (customer=" (:customer-name o) ")")))
     :rationale  (if o
                   (str "order-total-actual=" (:order-total-actual o) " items=" (pr-str (:items o)))
                   "注文記録が見つかりません")
     :cites      (if o [subject] [])
     :effect     :order/mark-fulfilled
     :value      {:order-id subject}
     :stake      :actuation/fulfill-order
     :confidence (if (and o (not (registry/order-total-mismatch? o))) 0.9 0.3)}))

(defn- propose-sanitization-certificate
  "Draft the actual CERTIFICATE-OF-DATA-DESTRUCTION action -- issuing a
  real Certificate of Data Destruction certifying a trade-in device as
  sanitized per NIST SP 800-88 Rev. 2. ALWAYS `:stake :actuation/
  issue-sanitization-certificate` -- this is a REAL-WORLD act, never a
  draft the actor may auto-run. See README `Actuation`: no phase ever
  adds this op to a phase's `:auto` set (`techretail.phase`); the
  governor also always escalates on `:actuation/issue-sanitization-
  certificate`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [u (store/trade-in-unit db subject)]
    {:summary    (str subject " 向けデータ消去証明書発行提案"
                      (when u (str " (device=" (:device-model u) ")")))
     :rationale  (if u
                   "NIST SP 800-88 Rev. 2 sanitization mission referenced"
                   "トレードイン機記録が見つかりません")
     :cites      (if u [subject] [])
     :effect     :trade-in-unit/mark-sanitization-certified
     :value      {:trade-in-unit-id subject}
     :stake      :actuation/issue-sanitization-certificate
     :confidence (if u 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :order/intake                               (normalize-order-intake db request)
    :consumer-protection-rules/verify           (verify-consumer-protection db request)
    :trade-in-condition/screen                  (screen-trade-in-condition db request)
    :robotics/simulate-data-wipe                (simulate-data-wipe db request)
    :robotics/simulate-drop-test                (simulate-drop-test db request)
    :actuation/fulfill-order                    (propose-fulfill-order db request)
    :actuation/issue-sanitization-certificate   (propose-sanitization-certificate db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたはコンピュータ小売店の注文発送・データ消去証明書発行エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:order/upsert|:trade-in-unit/upsert|"
       ":consumer-protection-verification/set|:trade-in-condition-screen/set|"
       ":order/mark-fulfilled|:trade-in-unit/mark-sanitization-certified) "
       "(:robotics/simulate-data-wipe も :trade-in-unit/upsert で "
       ":sanitization-sim-verified? を提案する。"
       ":robotics/simulate-drop-test も :trade-in-unit/upsert で "
       ":drop-test-sim-verified? / :sim-impact-decel-g を提案する) "
       ":stake(:actuation/fulfill-order か :actuation/issue-sanitization-certificate か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :consumer-protection-rules/verify           {:order (store/order st subject)}
    :trade-in-condition/screen                  {:trade-in-unit (store/trade-in-unit st subject)}
    :robotics/simulate-data-wipe                {:trade-in-unit (store/trade-in-unit st subject)}
    :robotics/simulate-drop-test                {:trade-in-unit (store/trade-in-unit st subject)}
    :actuation/fulfill-order                    {:order (store/order st subject)}
    :actuation/issue-sanitization-certificate   {:trade-in-unit (store/trade-in-unit st subject)}
    {:order (store/order st subject) :trade-in-unit (store/trade-in-unit st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Retail Governor escalates/
  holds -- an LLM hiccup can never auto-fulfill an order or auto-issue
  a Certificate of Data Destruction."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :techretailadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
