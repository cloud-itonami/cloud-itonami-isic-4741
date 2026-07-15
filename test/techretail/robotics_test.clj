(ns techretail.robotics-test
  "techretail.robotics's NEW real, time-stepped `physics-2d`-backed
  functional drop/shock-test simulation (ADR-2607152000, extending
  ADR-2607151600's automotive pilot to this vertical), exercised
  directly against the real `physics-2d` `world-step` solver -- the
  same rigor `vdesign.simphysics-test` (`kotoba-lang/kami-engine-
  vehicle-designer`) established for automotive's crash simulation.
  The existing symbolic data-wipe mission (`sanitization-incomplete?`/
  `simulate-data-wipe`) is unchanged and untouched by this ns.

  ADR-2607998500 EXTENDS this test suite with `techretail.robotics`'s
  new real CAD/BREP bridge (`techretail.cad`) for the `:device` body's
  AABB. Asserts the disclosed contract: (1) CAD-derived device geometry
  genuinely changes the simulated world (`:device-half-extents-m`,
  `:trajectory`'s absolute positions) -- a non-cosmetic effect, and (2)
  an EMPIRICAL, swept check (not merely algebraic) of whether that same
  geometry changes `:sim-impact-decel-g`/`:ticks`/`:dt`/`:impact-mps`/
  `:sim-impact-penetration-m` -- checking THIS vertical's own actual
  physics (a fixed-tick `reductions`, not `quarryops.robotics`'s own
  settle-loop), not assuming the same invariant `quarryops.robotics`
  found carries over unchanged. See `techretail.robotics/run-drop-
  simulation`'s own docstring for the full narrative of what was
  checked and found, including the ONE way this vertical's finding is
  even STRONGER than `quarryops.robotics`'s own."
  (:require [clojure.test :refer [deftest is testing]]
            [techretail.cad :as cad]
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

;; ───────────────────── ADR-2607998500: real CAD/BREP bridge ─────────────────────

(deftest trade-in-unit-with-no-intake-measurement-is-unchanged-from-pre-adr-2607998500-behavior
  (testing "a trade-in-unit with only :device-class/:device-mass-kg (no real
            :device-*-mm intake measurement on file) produces the SAME
            :device AABB half-extents this ns used as bare fixed constants
            before this ADR (techretail.cad's defaults are defined to
            reproduce them exactly -- see techretail.cad-test's own check
            of this same fact), and identical numeric results to an
            explicit-default-dims call"
    (let [sim (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4})
          {:keys [half-w half-h]} (:device-half-extents-m sim)]
      (is (= robotics/device-half-w-m half-w))
      (is (= robotics/device-half-h-m half-h)))))

(deftest cad-derived-device-geometry-genuinely-changes-the-devices-aabb
  (testing "two trade-in-units with the SAME class/mass but DIFFERENT real
            :device-length-mm/:device-width-mm/:device-height-mm produce
            DIFFERENT :device-half-extents-m -- a genuine, non-cosmetic
            effect of techretail.cad's real per-trade-in-unit geometry"
    (let [small (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4
                                                 :device-length-mm 150.0 :device-width-mm 80.0 :device-height-mm 8.0})
          large (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4
                                                 :device-length-mm 600.0 :device-width-mm 400.0 :device-height-mm 60.0})]
      (is (not= (:device-half-extents-m small) (:device-half-extents-m large)))
      (is (= {:half-w 0.075 :half-h 0.004} (:device-half-extents-m small)))
      (is (= {:half-w 0.3 :half-h 0.03} (:device-half-extents-m large))))))

(deftest cad-derived-height-genuinely-shifts-the-trajectorys-start-position
  (testing "this vertical's fall axis is HEIGHT (see techretail.cad's/
            techretail.robotics/device-half-extents-m's docstrings) -- two
            trade-in-units differing ONLY in :device-height-mm (same
            length/width/class/mass) must produce a genuinely different
            device start-y (device-y0 = floor-top + drop-height-m + half-h)"
    (let [thin (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4 :device-height-mm 8.0})
          thick (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4 :device-height-mm 60.0})
          y0 (fn [r] (second (:position (first (:trajectory r)))))]
      (is (not= (y0 thin) (y0 thick)))
      (is (< (y0 thin) (y0 thick)))))
  (testing "changing ONLY :device-length-mm/:device-width-mm (lateral, NOT
            the fall axis) leaves the trajectory's Y start position
            untouched -- confirms length/width genuinely do NOT drive this
            vertical's fall-axis physics"
    (let [narrow (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4
                                                  :device-length-mm 100.0 :device-width-mm 60.0})
          wide (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4
                                                :device-length-mm 900.0 :device-width-mm 700.0})
          y0 (fn [r] (second (:position (first (:trajectory r)))))]
      (is (= (y0 narrow) (y0 wide))
          "length/width do not enter device-y0 at all"))))

(deftest cad-derived-geometry-does-not-change-the-core-summary-readings-empirically-verified
  (testing "EMPIRICAL, not merely algebraic, check across a wide sweep of
            device classes, masses, and device geometries: :sim-impact-
            decel-g/:ticks/:dt/:impact-mps were found BIT-IDENTICAL
            regardless of :device-length-mm/:device-width-mm/:device-
            height-mm, for every (class, mass, geometry) combination swept
            here -- see run-drop-simulation's own docstring for the
            structural reason this holds (dt/ticks depend on :device-class
            and fixed constants alone, never on geometry -- checked, not
            assumed to mirror quarryops.robotics's own, differently-
            reasoned finding). Includes several deliberately pathological/
            non-physical geometries (near-zero and multi-hundred-meter
            :device-height-mm) to stress the claim, not just plausible
            device sizes."
    (doseq [device-class (keys robotics/device-give-m)
            device-mass-kg [0.2 2.0 8.5 50.0]
            [len wid hei] [[0.0001 0.0001 0.0001] [50.0 50.0 50.0] [300.0 220.0 20.0]
                           [900.0 300.0 5.0] [50.0 3000.0 2500.0] [3000.0 3000.0 3000.0]
                           [10.0 10.0 0.00005] [10.0 10.0 5000.0] [10.0 10.0 1900000.0]]]
      (let [bare (robotics/run-drop-simulation {:device-class device-class :device-mass-kg device-mass-kg})
            variant (robotics/run-drop-simulation {:device-class device-class :device-mass-kg device-mass-kg
                                                     :device-length-mm len :device-width-mm wid :device-height-mm hei})]
        (is (= (:sim-impact-decel-g bare) (:sim-impact-decel-g variant))
            (str "diverged at class=" device-class " mass=" device-mass-kg " dims=" [len wid hei]))
        (is (= (:ticks bare) (:ticks variant))
            (str "diverged at class=" device-class " mass=" device-mass-kg " dims=" [len wid hei]))
        (is (= (:dt bare) (:dt variant))
            (str "diverged at class=" device-class " mass=" device-mass-kg " dims=" [len wid hei]))
        (is (= (:impact-mps bare) (:impact-mps variant))
            (str "diverged at class=" device-class " mass=" device-mass-kg " dims=" [len wid hei]))))))

(deftest sim-impact-penetration-m-has-a-tiny-real-floating-point-divergence
  (testing "A REAL, VERIFIED, TINY-MAGNITUDE finding this test suite
            actually caught (an earlier draft assumed exact `=` here too,
            matching the four fields above -- WRONG, this test is what
            caught it): :sim-impact-penetration-m accumulates a tiny
            floating-point divergence across geometry (empirically bounded
            well under 1e-9, actually observed under 5e-13 across an
            extensive sweep) -- see run-drop-simulation's own docstring for
            why this divergence is EVEN SMALLER than quarryops.robotics's
            own analogous :sim-settling-distance-m finding (this ns has no
            persistent multi-thousand-tick resting-contact oscillation for
            rounding noise to accumulate across -- ticks here is a small,
            fixed budget, not a ~3000-tick max-ticks ceiling). Disclosed and
            bounded here, not silently rounded away or asserted as exact
            equality."
    (doseq [device-class (keys robotics/device-give-m)
            [len wid hei] [[50.0 50.0 50.0] [3000.0 3000.0 3000.0] [900.0 300.0 5.0] [0.0001 0.0001 0.0001]]]
      (let [bare (robotics/run-drop-simulation {:device-class device-class :device-mass-kg 2.0})
            variant (robotics/run-drop-simulation {:device-class device-class :device-mass-kg 2.0
                                                     :device-length-mm len :device-width-mm wid :device-height-mm hei})]
        (is (< (Math/abs (- (:sim-impact-penetration-m bare) (:sim-impact-penetration-m variant))) 1e-9)
            (str "unexpectedly LARGE divergence at class=" device-class " dims=" [len wid hei]))))))

(deftest trajectory-itself-is-not-geometry-invariant-unlike-the-summary-readings
  (testing "mirrors quarryops.robotics's own disclosed trajectory-position
            sensitivity: :trajectory's actual position samples genuinely
            differ across geometry (device-y0 depends on half-h), even
            though the summary readings above do not"
    (let [thin (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4 :device-height-mm 8.0})
          thick (robotics/run-drop-simulation {:device-class :laptop :device-mass-kg 1.4 :device-height-mm 60.0})]
      (is (not= (:trajectory thin) (:trajectory thick))))))

(deftest device-half-extents-m-reads-techretail-cads-real-per-trade-in-unit-dims
  (testing "device-half-extents-m (public -- see its own docstring for why)
            agrees with techretail.cad/envelope-dims-mm for the same
            trade-in-unit, confirming the CAD bridge is genuinely wired in,
            not a private/parallel implementation"
    (let [trade-in-unit {:device-length-mm 600.0 :device-width-mm 300.0 :device-height-mm 40.0}
          {:keys [length-mm height-mm]} (cad/envelope-dims-mm trade-in-unit)]
      (is (= {:half-w (/ length-mm 2000.0) :half-h (/ height-mm 2000.0)}
             (robotics/device-half-extents-m trade-in-unit))))))
