(ns techretail.cad-test
  "techretail.cad's real BREP trade-in-device-envelope bridge
  (ADR-2607998500) -- envelope-dims-mm's real-intake-vs-fixed-default
  fallback discipline (NOTE: unlike quarryops.cad, the default here is
  a set of FIXED literals, not a formula of :device-mass-kg -- see
  techretail.cad's own docstring for why: techretail.robotics's
  pre-ADR AABB was already a bare fixed constant, not mass-derived),
  and envelope-solid/envelope-mesh's genuine tessellation output."
  (:require [clojure.test :refer [deftest is testing]]
            [techretail.cad :as cad]
            [techretail.robotics :as robotics]))

(deftest envelope-dims-mm-falls-back-to-the-disclosed-fixed-defaults-when-absent
  (testing "a trade-in-unit with no :device-*-mm fields gets the SAME
            AABB size techretail.robotics/device-half-w-m/device-half-
            h-m always used, before this ADR and after it"
    (let [dims (cad/envelope-dims-mm {:id "unit-x" :device-class :laptop :device-mass-kg 1.4})]
      (is (= cad/default-device-length-mm (:length-mm dims)))
      (is (= cad/default-device-width-mm (:width-mm dims)))
      (is (= cad/default-device-height-mm (:height-mm dims)))
      (is (< (Math/abs (- (* 2000.0 robotics/device-half-w-m) (:length-mm dims))) 1e-9)
          "default length exactly reproduces device-half-w-m * 2000")
      (is (< (Math/abs (- (* 2000.0 robotics/device-half-h-m) (:height-mm dims))) 1e-9)
          "default height exactly reproduces device-half-h-m * 2000")))
  (testing "nil trade-in-unit also falls back cleanly"
    (let [dims (cad/envelope-dims-mm nil)]
      (is (= cad/default-device-length-mm (:length-mm dims)))
      (is (= cad/default-device-width-mm (:width-mm dims)))
      (is (= cad/default-device-height-mm (:height-mm dims))))))

(deftest envelope-dims-mm-defaults-are-flat-not-mass-or-class-derived
  (testing "UNLIKE quarryops.cad's mass-derived-cube default, techretail's
            default envelope is IDENTICAL regardless of :device-class or
            :device-mass-kg -- a genuine, disclosed difference in design
            (this vertical's pre-ADR AABB was never mass/class-derived)"
    (is (= (cad/envelope-dims-mm {:device-class :handheld :device-mass-kg 0.3})
           (cad/envelope-dims-mm {:device-class :desktop-tower :device-mass-kg 8.5})
           (cad/envelope-dims-mm {})))))

(deftest envelope-dims-mm-uses-a-trade-in-units-own-real-intake-measurement-when-present
  (testing "an explicit :device-*-mm triple overrides the fixed default"
    (is (= {:length-mm 320.0 :width-mm 225.0 :height-mm 18.0}
           (cad/envelope-dims-mm {:device-class :laptop :device-mass-kg 1.4
                                   :device-length-mm 320.0
                                   :device-width-mm 225.0
                                   :device-height-mm 18.0}))))
  (testing "a partial triple only overrides the fields actually given -- the
            other two still fall back to the fixed defaults"
    (let [dims (cad/envelope-dims-mm {:device-length-mm 320.0})]
      (is (= 320.0 (:length-mm dims)))
      (is (= cad/default-device-width-mm (:width-mm dims)))
      (is (= cad/default-device-height-mm (:height-mm dims))))))

(deftest envelope-dims-mm-vary-per-trade-in-unit-when-real-measurements-differ
  (testing "two trade-in-units with different real intake measurements get
            genuinely different envelopes -- this is not a fixed constant
            dressed up as per-unit data"
    (is (not= (cad/envelope-dims-mm {:device-length-mm 320.0 :device-width-mm 225.0 :device-height-mm 18.0})
              (cad/envelope-dims-mm {:device-length-mm 380.0 :device-width-mm 260.0 :device-height-mm 25.0})))))

(deftest envelope-solid-produces-real-tessellatable-geometry
  (let [{:keys [dims] :as solid} (cad/envelope-solid {:device-length-mm 320.0
                                                        :device-width-mm 225.0
                                                        :device-height-mm 18.0})]
    (is (= {:length-mm 320.0 :width-mm 225.0 :height-mm 18.0} dims))
    (is (seq (:vertices solid)))
    (is (seq (:edges solid)))
    (testing "the tessellated footprint's X/Y extent matches the requested dims (mm)"
      (let [{:keys [positions]} (cad/envelope-mesh solid)
            extent (fn [axis] (- (apply max (map #(nth % axis) positions))
                                  (apply min (map #(nth % axis) positions))))]
        (is (< (Math/abs (- (extent 0) 320.0)) 1e-6))
        (is (< (Math/abs (- (extent 1) 225.0)) 1e-6))))))

(deftest envelope-mesh-is-well-formed
  (let [solid (cad/envelope-solid {:device-class :laptop :device-mass-kg 1.4})
        {:keys [positions indices]} (cad/envelope-mesh solid)]
    (is (pos? (count positions)))
    (is (pos? (count indices)))
    (is (zero? (mod (count indices) 3)) "indices are complete triangles")
    (is (every? #(<= 0 % (dec (count positions))) indices)
        "every index references a valid vertex")
    (is (every? #(= 3 (count %)) positions) "positions are [x y z]")))
