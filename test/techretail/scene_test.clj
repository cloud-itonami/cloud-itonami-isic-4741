(ns techretail.scene-test
  "techretail.scene's bridge from techretail.cad's tessellated envelope +
  techretail.robotics/run-drop-simulation's trajectory into
  kami.webgpu.mesh's real input shape, asserted for well-formedness --
  no browser/WebGPU device is available in this JVM/.cljc actor repo
  (see techretail.scene's docstring). Direct port of autoparts.scene-
  test's/fab.scene-test's/quarryops.scene-test's own assertions
  (ADR-2607160000/ADR-2607995500), adapted to a plain trade-in-unit map."
  (:require [clojure.test :refer [deftest is testing]]
            [techretail.robotics :as robotics]
            [techretail.scene :as scene]))

(def ^:private sample-trade-in-unit
  {:id "unit-scene-test" :device-class :laptop :device-mass-kg 1.4
   :device-length-mm 320.0 :device-width-mm 225.0 :device-height-mm 18.0})

(deftest mesh-data-is-well-formed
  (testing "positions/normals/indices satisfy kami.webgpu.mesh/upload-mesh!'s
            real contract: same-length positions/normals, index count a
            multiple of 3, every index within the vertex range"
    (let [{:keys [positions normals indices vertex-count index-count]} (scene/scene-for sample-trade-in-unit)]
      (is (pos? vertex-count))
      (is (pos? index-count))
      (is (= (count positions) vertex-count))
      (is (= (count normals) vertex-count)
          "upload-mesh! requires one normal per vertex, not optional like uvs/skin/morph")
      (is (= (count indices) index-count))
      (is (zero? (mod index-count 3)))
      (is (every? #(<= 0 % (dec vertex-count)) indices)
          "every index must reference a valid vertex")
      (is (every? #(= 3 (count %)) positions) "positions are [x y z]")
      (is (every? #(= 3 (count %)) normals) "normals are [x y z]")
      (is (every? (fn [n] (< (Math/abs (- 1.0 (Math/sqrt (reduce + (map * n n))))) 1e-6)) normals)
          "every normal must actually be unit-length"))))

(deftest one-frame-per-simulated-tick
  (testing "one :transform per techretail.robotics/run-drop-simulation
            trajectory tick"
    (let [sim (robotics/run-drop-simulation sample-trade-in-unit)
          sc (scene/scene-for sample-trade-in-unit)]
      (is (= (:ticks sim) (count (:frames sc))))
      (is (every? #(= 3 (count (get-in % [:transform :translation]))) (:frames sc)))
      (is (every? #(= [0.0 0.0 0.0] (get-in % [:transform :rotation])) (:frames sc))
          "physics-2d has no orientation state -- every frame's rotation is identity, honestly")
      (is (every? #(= [1.0 1.0 1.0] (get-in % [:transform :scale])) (:frames sc)))
      ;; translations move: the scene isn't rendering a frozen frame (the
      ;; device free-falls then impacts, unlike a static point-test).
      (is (not= (get-in (first (:frames sc)) [:transform :translation])
                (get-in (last (:frames sc)) [:transform :translation]))))))

(deftest mesh-is-unit-converted-to-meters-and-already-centered-in-xy
  (testing "the mesh's XY footprint extent (now in METERS, matching
            techretail.robotics's trajectory units) still matches the real
            envelope-dims-mm length/width (converted mm->m); X/Y are
            naturally centered on the local origin already (techretail.cad's
            +/-0.5-unit-square sketch convention -- see techretail.scene's
            docstring)"
    (let [{:keys [positions dims]} (scene/scene-for sample-trade-in-unit)
          extent (fn [axis] (- (apply max (map #(nth % axis) positions))
                                (apply min (map #(nth % axis) positions))))]
      (is (< (Math/abs (- (extent 0) (/ (:length-mm dims) 1000.0))) 1e-6))
      (is (< (Math/abs (- (extent 1) (/ (:width-mm dims) 1000.0))) 1e-6))
      ;; centered: min/max along X (and Y) are symmetric around 0.
      (is (< (Math/abs (+ (apply min (map #(nth % 0) positions))
                          (apply max (map #(nth % 0) positions))))
             1e-6)))))

(deftest scene-for-falls-back-to-techretail-robotics-own-defaults-when-fields-absent
  (testing "a trade-in-unit with no :device-class/:device-mass-kg/:device-*-mm
            at all still works through scene-for -- techretail.robotics/
            run-drop-simulation is already nil-safe (see its own docstring),
            and techretail.cad falls back to its own fixed default for the
            envelope dims"
    (let [sc (scene/scene-for {:id "unit-bare"})]
      (is (pos? (:vertex-count sc)))
      (is (pos? (:index-count sc)))))
  (testing "nil trade-in-unit also works"
    (let [sc (scene/scene-for nil)]
      (is (pos? (:vertex-count sc)))
      (is (pos? (:index-count sc))))))
