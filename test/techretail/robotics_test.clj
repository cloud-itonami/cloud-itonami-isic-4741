(ns techretail.robotics-test
  "techretail.robotics's NEW real, time-stepped `physics-2d`-backed
  functional drop/shock-test simulation (ADR-2607152000, extending
  ADR-2607151600's automotive pilot to this vertical), exercised
  directly against the real `physics-2d` `world-step` solver -- the
  same rigor `vdesign.simphysics-test` (`kotoba-lang/kami-engine-
  vehicle-designer`) established for automotive's crash simulation.
  The existing symbolic data-wipe mission (`sanitization-incomplete?`/
  `simulate-data-wipe`) is unchanged and untouched by this ns."
  (:require [clojure.test :refer [deftest is testing]]
            [techretail.robotics :as robotics]))

(deftest trajectory-actually-evolves
  (testing "the trajectory is a real per-tick free-fall simulation output, not a no-op -- position changes across ticks, the device actually falls (loses altitude) then gains real downward speed under gravity before settling back to rest on impact"
    (let [{:keys [trajectory ticks impact-mps]} (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4})
          first-t (first trajectory)
          last-t (last trajectory)
          peak-speed (reduce max 0.0 (map (comp #(Math/abs (double %)) second :velocity) trajectory))]
      (is (> ticks 1))
      (is (= ticks (count trajectory)))
      (is (not= (:position first-t) (:position last-t))
          "the device body must actually move over the simulated ticks")
      (is (< (second (:position last-t)) (second (:position first-t)))
          "the device must have fallen -- lower altitude at the last tick than the first")
      (is (= [0.0 0.0] (:velocity first-t))
          "the device starts at rest, before real gravity integration accelerates it")
      (is (> peak-speed (* 0.9 impact-mps))
          "real gravity integration must accelerate the device to close to the real kinematic impact speed before it hits the test-surface")
      ;; settles: by the last tick the device is resting again, not still
      ;; moving at whatever speed it had mid-fall/at-impact -- the whole
      ;; point of appending settle-ticks (positional correction converges the
      ;; residual overlap, and post-impact velocity is zeroed by inelastic
      ;; resolution).
      (is (< (Math/abs (double (second (:velocity last-t)))) 0.5)
          "by the last tick the device must have settled to near-zero velocity (impact + settling actually happened)"))))

(deftest mass-alone-does-not-change-impact-decel-g
  (testing "documented, verified finding (namespace docstring): colliding with a mass-0 (immovable) test-surface, physics_2d's impulse resolution is independent of the falling body's own mass -- a genuinely much heavier device at the SAME device-class produces the SAME :sim-impact-decel-g, not a fabricated heavier-implies-higher relationship"
    (let [light (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 0.5})
          heavy (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 50.0})]
      (is (< (Math/abs (- (:sim-impact-decel-g light) (:sim-impact-decel-g heavy))) 1e-6)))))

(deftest device-class-give-is-the-real-lever
  (testing "the device's own :device-class (assumed internal shock-absorbing give), NOT its mass, is what actually moves :sim-impact-decel-g in this model -- a class with LESS assumed give must show a genuinely HIGHER simulated impact deceleration at the SAME fixed drop-height-m"
    (let [handheld (robotics/drop-test-telemetry-for {:device-class :handheld :device-mass-kg 0.3})
          laptop   (robotics/drop-test-telemetry-for {:device-class :laptop :device-mass-kg 1.4})
          monitor  (robotics/drop-test-telemetry-for {:device-class :monitor :device-mass-kg 6.0})
          mini     (robotics/drop-test-telemetry-for {:device-class :desktop-mini :device-mass-kg 3.5})]
      (is (< (:sim-impact-decel-g handheld) (:sim-impact-decel-g laptop)))
      (is (< (:sim-impact-decel-g laptop) (:sim-impact-decel-g monitor)))
      (is (< (:sim-impact-decel-g monitor) (:sim-impact-decel-g mini))))))

(deftest crosscheck-is-within-the-documented-boxcar-ratio-band-for-every-class
  (testing "run-drop-simulation's time-stepped boxcar :sim-impact-decel-g stays within the documented ~2x kinematic-identity band of closed-form-impact-decel-g, for every real device class this ns defines -- a coarse sanity crosscheck, not a validation of either idealization's absolute accuracy (see crosscheck's docstring)"
    (doseq [device-class (keys robotics/device-give-m)]
      (let [xc (robotics/crosscheck {:device-class device-class :device-mass-kg 2.0})]
        (is (pos? (:sim-impact-decel-g xc)))
        (is (pos? (:closed-form-decel-g xc)))
        (is (:within-tolerance? xc)
            (str device-class " ratio " (:ratio xc) " outside ["
                 robotics/crosscheck-ratio-low ", " robotics/crosscheck-ratio-high "]"))
        (is (< (Math/abs (- (:ratio xc) 2.0)) 0.05)
            (str device-class " ratio " (:ratio xc)
                 " should be very close to the exact 2x boxcar-vs-ramp kinematic identity"))))))

(deftest handheld-laptop-monitor-clear-tolerance-desktop-mini-does-not
  (testing "at the standard 1.0 m drop-height-m, :handheld/:laptop/:monitor/:desktop-tower all clear decel-ceiling-g with real margin, but :desktop-mini's minimal assumed give genuinely exceeds it -- a real simulated finding, not a hand-set fake field (mirrors automotive.robotics/decel-ceiling-g's own per-class empirical survey)"
    (doseq [ok-class [:handheld :laptop :monitor :desktop-tower]]
      (is (not (robotics/drop-test-impact-out-of-tolerance?
                (robotics/drop-test-telemetry-for {:device-class ok-class :device-mass-kg 2.0})))
          (str ok-class " must clear decel-ceiling-g")))
    (is (robotics/drop-test-impact-out-of-tolerance?
         (robotics/drop-test-telemetry-for {:device-class :desktop-mini :device-mass-kg 3.5}))
        ":desktop-mini's minimal give must genuinely exceed decel-ceiling-g at the standard drop height")))

(deftest simulate-drop-test-mission-shape
  (testing "simulate-drop-test runs the real mission end-to-end and reports a verdict consistent with the independent ground-truth check"
    (let [{:keys [mission actions passed? sim-impact-decel-g]}
          (robotics/simulate-drop-test "unit-1" {:device-class :laptop :device-mass-kg 1.4})]
      (is (= 3 (count actions)))
      (is (= :trade-in-functional-drop-shock-test (:mission/objective mission)))
      (is (true? passed?))
      (is (number? sim-impact-decel-g))
      (is (not (robotics/drop-test-out-of-tolerance? {:sim-impact-decel-g sim-impact-decel-g}))))))
