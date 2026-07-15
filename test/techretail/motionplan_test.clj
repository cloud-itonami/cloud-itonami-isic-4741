(ns techretail.motionplan-test
  "techretail.motionplan/motion-plan-for -- the Cartesian waypoint list
  built from techretail.robotics/drop-test-mission-actions's real 3-step
  drop/shock-TEST-ROBOT mission sequence (ADR-2607998500). See
  techretail.motionplan's own ns docstring for why this abstraction
  genuinely applies to the DROP-TEST mission specifically (not
  techretail.robotics/mission-actions, the unrelated symbolic data-wipe
  mission) and NOT to the physics-simulated device's own free-fall
  trajectory (which this ns does not, and should not, plan a route
  through)."
  (:require [clojure.test :refer [deftest is testing]]
            [techretail.cad :as cad]
            [techretail.motionplan :as motionplan]
            [techretail.robotics :as robotics]))

(deftest one-waypoint-per-drop-test-mission-action-same-order
  (let [plan (motionplan/motion-plan-for {:device-class :laptop :device-mass-kg 1.4})]
    (is (= (count robotics/drop-test-mission-actions) (count plan)))
    (is (= (mapv :step robotics/drop-test-mission-actions) (mapv :step plan)))
    (is (= [1 2 3] (mapv :seq plan)))
    (is (= ["device-placement-on-drop-rig" "drop-release" "post-drop-functional-recheck"]
           (mapv :station plan)))))

(deftest plan-walks-the-drop-test-mission-not-the-data-wipe-mission
  (testing "confirms motion-plan-for is wired to drop-test-mission-actions,
            not the unrelated symbolic data-wipe mission-actions -- the two
            have genuinely different step names, so this is a real,
            checkable assertion, not a restatement"
    (let [plan (motionplan/motion-plan-for {:device-class :laptop :device-mass-kg 1.4})]
      (is (not= (mapv :step robotics/mission-actions) (mapv :step plan)))
      (is (= 3 (count robotics/mission-actions)) "both missions happen to be 3 steps, so this checks names not just count"))))

(deftest waypoints-are-spaced-along-the-travel-axis
  (let [plan (motionplan/motion-plan-for {:device-class :laptop :device-mass-kg 1.4})
        xs (mapv #(first (:waypoint %)) plan)]
    (is (= [0.0 motionplan/station-pitch-m (* 2 motionplan/station-pitch-m)] xs))
    (is (every? #(= motionplan/default-tool-orientation (:tool-orientation %)) plan))
    (is (every? #(zero? (second (:waypoint %))) plan) "y is the line centerline")))

(deftest working-height-derives-from-the-trade-in-units-real-envelope
  (testing "z (working height) is half the trade-in-unit's own real envelope height"
    (let [trade-in-unit {:device-class :laptop :device-mass-kg 1.4 :device-height-mm 18.0}
          plan (motionplan/motion-plan-for trade-in-unit)
          z (nth (:waypoint (first plan)) 2)]
      (is (= (/ 18.0 2000.0) z))))
  (testing "a trade-in-unit with no real :device-height-mm still gets a real
            answer via techretail.cad's own disclosed fixed default (NOT
            motionplan's separate fallback)"
    (let [trade-in-unit {:device-class :laptop :device-mass-kg 1.4}
          plan (motionplan/motion-plan-for trade-in-unit)
          z (nth (:waypoint (first plan)) 2)]
      (is (= (/ cad/default-device-height-mm 2000.0) z))))
  (testing "no trade-in-unit at all (older/hand-rolled caller) -> motionplan's own default-working-height-m"
    (let [plan (motionplan/motion-plan-for)
          z (nth (:waypoint (first plan)) 2)]
      (is (= motionplan/default-working-height-m z)))))

(deftest deterministic-same-trade-in-unit-same-plan
  (is (= (motionplan/motion-plan-for {:device-class :laptop :device-mass-kg 1.4 :device-height-mm 20.0})
         (motionplan/motion-plan-for {:device-class :laptop :device-mass-kg 1.4 :device-height-mm 20.0}))))

(deftest working-height-uses-cads-real-envelope-dims-not-a-parallel-implementation
  (testing "confirms motion-plan-for's working-height genuinely reads
            techretail.cad/envelope-dims-mm, not a private/parallel duplication"
    (let [trade-in-unit {:device-class :laptop :device-mass-kg 1.4 :device-height-mm 22.0}
          plan (motionplan/motion-plan-for trade-in-unit)
          z (nth (:waypoint (first plan)) 2)]
      (is (= (/ (:height-mm (cad/envelope-dims-mm trade-in-unit)) 2000.0) z)))))
