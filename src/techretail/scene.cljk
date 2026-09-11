(ns techretail.scene
  "Bridge from `techretail.cad`'s tessellated device-envelope mesh +
  `techretail.robotics/run-drop-simulation`'s per-tick physics
  trajectory (`:device` body only) into the vertex/index/per-frame-
  transform data shape `kotoba-lang/webgpu`'s `kami.webgpu.mesh`
  executor's REAL, working `upload-mesh!`/`render-frame!` functions
  already consume (ADR-2607998500, closing out the 6-vertical digital-
  twin wave ADR-2607160000 started -- direct port of `autoparts.scene`'s/
  `fab.scene`'s/`quarryops.scene`'s reference pattern to this vertical's
  own trade-in-device functional drop/shock-test case, itself a port of
  `kami-engine-vehicle-designer`'s `vdesign.scene`, ADR-2607151600; see
  `techretail.cad`/`techretail.robotics` docstrings for that same
  porting rationale).

  `:positions`/`:normals`/`:indices` in `scene-for`'s result are
  DIRECTLY the shape `kami.webgpu.mesh/upload-mesh!` destructures
  (`{:keys [positions normals indices uvs morph-target-deltas joints
  weights]}`, all but `:positions`/`:normals`/`:indices` optional) --
  `(select-keys (scene-for trade-in-unit) [:positions :normals
  :indices])` is a drop-in `geometry` argument for that function today.
  `:frames`'s per-entry `:transform` map (`{:translation [x y z]
  :rotation [rx ry rz] :scale [sx sy sz]}`) is DIRECTLY the shape
  `kami.webgpu.mesh/model-matrix` (and `render-frame!`'s optional
  trailing `transform` arg, handed straight to `model-matrix`) expects
  -- one `:frames` entry per `techretail.robotics/run-drop-simulation`
  trajectory tick. `sim-opts` is accepted but unused by this ns's own
  `run-drop-simulation`, which takes no tuning opts of its own (mirrors
  `quarryops.scene`'s own ignored trailing arg, kept only for call-shape
  parity with `autoparts.scene`'s/`fab.scene`'s own `opts` argument).

  Two REAL, disclosed gaps close this from being a byte-for-byte
  drop-in -- the SAME two gaps `vdesign.scene`/`autoparts.scene`/
  `fab.scene`/`quarryops.scene` close, ported here verbatim because the
  underlying mismatch is identical:

  1. `techretail.cad/envelope-mesh` produces `{:positions :indices}`
     only -- no `:normals`. `kami.webgpu.mesh/upload-mesh!` requires
     `:normals` (the same length as `:positions` -- a mandatory vertex
     attribute the shader reads, not optional like `:uvs`/skin/morph).
     `face-normals` below computes REAL per-triangle flat normals
     (cross product of each triangle's own two edges) to close this
     gap -- not a placeholder/constant normal.
  2. `techretail.cad/envelope-mesh`'s positions are in MILLIMETERS
     (`techretail.cad/envelope-dims-mm` derives `:length-mm`/`:width-mm`/
     `:height-mm`, and `envelope-solid`'s `scale-point` scales the base
     sketch by those mm figures directly) while `techretail.robotics/
     run-drop-simulation`'s trajectory positions are in METERS
     (`physics-2d` is unit-agnostic, but `techretail.robotics` chose
     meters -- see `device-half-extents-m`, which divides by 2000.0 to
     go mm -> half-extent-in-meters). Combining raw mm vertex geometry
     with a meter-scale per-frame translation would place the mesh
     ~1000x too large relative to its own motion. `mesh->m` below
     converts the tessellated positions to meters to close this real
     unit mismatch.

  A THIRD, real, DISCLOSED axis-convention mismatch, the SAME one
  `quarryops.scene`'s own docstring discloses for its vertical free-
  fall case (not present in `autoparts.scene`'s/`fab.scene`'s own
  horizontal-pull case): `techretail.cad/envelope-solid` extrudes along
  CAD-local Z (`techretail.cad`'s own docstring: 'sketch on XY ...
  extrude along Z' -- the SAME convention `autoparts.cad`/`fab.cad`/
  `quarryops.cad`/`vdesign.cad` use), but `techretail.robotics`'s
  physics world is 2D-Y-up (gravity along `world-gravity` `[0.0
  -9.81]`, the device's fall axis is Y). This ns does NOT reconcile the
  two (no rotation is applied to align the mesh's Z-extrude axis with
  the physics world's Y-fall axis) -- `:frames`' `:transform` below is
  translation-only, exactly like `autoparts.scene`'s/`fab.scene`'s/
  `quarryops.scene`'s own disclosed 'every frame's :rotation is [0 0 0]'
  limitation (`physics-2d`'s `Body2D` carries NO orientation state at
  all to rotate FROM in the first place).

  Same box-footprint-centering property `vdesign.scene`/`autoparts.
  scene`/`fab.scene`/`quarryops.scene` document (verified, not assumed,
  by `scene_test.clj` below): `techretail.cad/envelope-solid`
  tessellates from `brep.feature`'s documented +/-0.5-unit-square
  sketch, so the footprint is ALREADY centered on the local origin in
  X/Y (min-x = -length/2, max-x = +length/2) -- only Z spans
  `[0,height]` uncentered, the extrude direction. No XY shift is needed
  or applied; only the mm->m unit conversion.

  Remaining, honest limitation (same as `vdesign.scene`'s/`autoparts.
  scene`'s/`fab.scene`'s/`quarryops.scene`'s): `kami.webgpu.mesh` itself
  is a `.cljs`-only WebGPU executor (`js/Float32Array`, real GPU
  device/buffer calls) -- actually calling `upload-mesh!`/
  `render-frame!` needs a ClojureScript/browser host loading this
  namespace's output and iterating `:frames`, which this JVM-`.cljc`
  actor repo (no browser here) cannot execute, and `kotoba-lang/webgpu`
  is deliberately NOT a runtime dependency of this repo (see deps.edn).
  The DATA SHAPE this namespace produces is genuinely, verifiably
  compatible with that function's real input contract (see `scene_
  test.clj`); wiring it into a live canvas is the small host-side step
  that remains, and is NOT claimed to be done here.

  Also disclosed: every frame's `:rotation` is `[0 0 0]` and `:scale`
  is `[1 1 1]` -- `physics-2d`'s `Body2D` carries NO orientation/
  angular state at all (translation-only rigid body), a real property
  of the underlying solver, not something simplified away by this
  bridge."
  (:require [techretail.cad :as cad]
            [techretail.robotics :as robotics]))

(defn- v-sub [[ax ay az] [bx by bz]] [(- bx ax) (- by ay) (- bz az)])

(defn- v-cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])

(defn- v-length [[x y z]]
  #?(:clj  (Math/sqrt (double (+ (* x x) (* y y) (* z z))))
     :cljs (js/Math.sqrt (+ (* x x) (* y y) (* z z)))))

(defn- flat-normal
  "Real geometric face normal for triangle `a b c` -- cross product of
  two edges, normalized. Falls back to `[0 0 1]` only for a degenerate
  (zero-area) triangle."
  [a b c]
  (let [n (v-cross (v-sub a b) (v-sub a c))
        len (v-length n)]
    (if (pos? len)
      (mapv #(/ % len) n)
      [0.0 0.0 1.0])))

(defn face-normals
  "Per-vertex flat (face) normals for `positions`/`indices`
  (`techretail.cad/envelope-mesh`'s output shape) -- REAL geometric
  normals computed from each triangle's own 3 vertices, not a
  placeholder or constant. Safe to assign one flat normal per triangle-
  vertex here because `brep.tessellate/tessellate-solid` gives every
  BREP face (this envelope's sketch/extrude box faces) its own PRIVATE
  vertex range -- no vertex index is shared between two faces with
  different normals for this shape, so writing a vertex's normal once
  per triangle it appears in never produces a wrong final value (same
  reasoning `autoparts.scene/face-normals`/`fab.scene/face-normals`/
  `quarryops.scene/face-normals`/`vdesign.scene/face-normals` document,
  unchanged here)."
  [positions indices]
  (let [tris (partition 3 indices)]
    (reduce
     (fn [normals [ia ib ic]]
       (let [n (flat-normal (nth positions ia) (nth positions ib) (nth positions ic))]
         (-> normals (assoc ia n) (assoc ib n) (assoc ic n))))
     (vec (repeat (count positions) [0.0 0.0 1.0]))
     tris)))

(defn- mesh->m
  "Converts `techretail.cad`'s millimeter-scale tessellated positions to
  meters, matching `techretail.robotics`'s meter-scale trajectory
  positions -- see namespace docstring, gap 2. A uniform positive scale
  never changes face-normal directions, so callers may compute `face-
  normals` before or after this conversion; `scene-for` converts first,
  for positions that are already in the same units as `:frames`'
  translations."
  [positions]
  (mapv (fn [[x y z]] [(/ x 1000.0) (/ y 1000.0) (/ z 1000.0)]) positions))

(defn scene-for
  "Builds
    {:positions [...] :normals [...] :indices [...]
     :frames [{:tick n :transform {:translation [x y z]
                                   :rotation [0.0 0.0 0.0]
                                   :scale [1.0 1.0 1.0]}} ...]
     :vertex-count n :index-count n :dims {...}}
  for `trade-in-unit` -- the real tessellated device envelope
  (`techretail.cad/envelope-solid`/`envelope-mesh`), unit-converted to
  meters and given real face normals, plus one frame per `techretail.
  robotics/run-drop-simulation` trajectory tick. `:dims` is
  `techretail.cad`'s own millimeter-scale `:dims` (`:length-mm`/
  `:width-mm`/`:height-mm`), kept as informational metadata, NOT the
  unit `:positions` is in. `trade-in-unit` should be the full trade-in-
  unit record; absent `:device-class`/`:device-mass-kg` fall back to
  `techretail.robotics`'s own disclosed defaults directly inside
  `run-drop-simulation` (UNLIKE `quarryops.scene`, this ns needs no
  separate default-resolution step of its own -- `techretail.robotics/
  run-drop-simulation` is already nil-safe: `give-for` defaults an
  absent/unknown `:device-class` to `:laptop`'s give, and `:device-
  mass-kg` defaults to 1.0 -- checked directly against `run-drop-
  simulation`'s own source, not assumed to need the same `fragment-for`-
  style pre-resolution `quarryops.scene` needs). `sim-opts` is accepted
  but currently ignored (see ns docstring). See namespace docstring for
  exactly which fields are a direct drop-in for `kami.webgpu.mesh`
  today vs. the disclosed adapter gaps this namespace closes."
  [trade-in-unit & [_sim-opts]]
  (let [solid (cad/envelope-solid trade-in-unit)
        {:keys [positions indices]} (cad/envelope-mesh solid)
        positions (mesh->m positions)
        normals (face-normals positions indices)
        sim (robotics/run-drop-simulation trade-in-unit)
        frames (mapv (fn [{:keys [tick position]}]
                       {:tick tick
                        :transform {:translation [(first position) (second position) 0.0]
                                    :rotation [0.0 0.0 0.0]
                                    :scale [1.0 1.0 1.0]}})
                     (:trajectory sim))]
    {:positions positions
     :normals normals
     :indices indices
     :frames frames
     :vertex-count (count positions)
     :index-count (count indices)
     :dims (:dims solid)}))
