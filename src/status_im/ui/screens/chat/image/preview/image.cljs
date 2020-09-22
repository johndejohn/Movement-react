(ns status-im.ui.screens.chat.image.preview.image
  (:require [reagent.core :as reagent]
            [cljs-bean.core :as bean]
            [quo.react :as react]
            [quo.animated :as animated]
            [quo.vectors :as vec]
            [quo.gesture-handler :as gh]))

(def min-scale 1)
(def max-scale 3)
(def snap-duration 150)
(def overscale-duration 150)
(def swipe-close-at 0.6)
(def overdrag-factor 0.2)

(defn decay
  [position velocity clock]
  (let [state  #js {:finished (animated/value 0)
                    :position (animated/value 0)
                    :time     (animated/value 0)
                    :velocity (animated/value 0)}
        config #js {:deceleration 0.98}]
    (animated/block
     [(animated/cond* (animated/not* (animated/clock-running clock))
                      [(animated/set (.-finished state) 0)
                       (animated/set (.-position state) position)
                       (animated/set (.-velocity state) velocity)
                       (animated/set (.-time state) 0)
                       (animated/start-clock clock)])
      (animated/decay clock state config)
      (.-position state)])))

(defn decay-vector
  [position velocity clock]
  (let [x (decay (.-x position) (.-x velocity) (.-x clock))
        y (decay (.-y position) (.-y velocity) (.-y clock))]
    #js {:x x
         :y y}))

(defn use-pinch
  [pinch pan translate translation scale min-vec canvas]
  (let [should-decay      (animated/use-value 0)
        scale-clock       (animated/clock)
        clock             (vec/create (animated/clock) (animated/clock))
        offset            (vec/create-value 0 0)
        scale-offset      (animated/value 1)
        origin            (vec/create-value 0 0)
        local-translation (vec/create-value 0 0)
        max-vec           (vec/minus min-vec)
        center            (vec/divide canvas 2)
        adjustedFocal     (vec/sub (:focal pinch) (vec/add center offset))
        clamped           (vec/sub
                           (vec/clamp (vec/add offset (:translation pan)) min-vec max-vec)
                           offset)
        overdrag-vector   (vec/create (animated/cond* (animated/eq (.-x clamped) 0) overdrag-factor 1)
                                      (animated/cond* (animated/eq (.-y clamped) 0) overdrag-factor 1))
        pinch-began       (animated/pinch-began (:state pinch))
        pinch-active      (animated/pinch-active (:state pinch) (:number-of-pointers pinch))
        pinch-end         (animated/pinch-end (:state pinch) (:number-of-pointers pinch))]
    (animated/code!
     (fn []
       (animated/block
        [(animated/cond* (animated/eq (:state pan) (:active gh/states))
                         [(vec/set translation (vec/sub (:translation pan) clamped))
                          (vec/set local-translation
                                   (vec/multiply (:translation pan) overdrag-vector))])
         (animated/cond* pinch-began (vec/set origin adjustedFocal))
         (animated/cond* pinch-active
                         (vec/set
                          local-translation
                          (vec/add
                           (vec/sub adjustedFocal origin)
                           origin
                           (vec/multiply -1 (:scale pinch) origin))))
         (animated/cond* (animated/and*
                          (animated/or*
                           (animated/eq (:state pinch) (:undetermined gh/states))
                           pinch-end)
                          (animated/or*
                           (animated/eq (:state pan) (:undetermined gh/states))
                           (animated/eq (:state pan) (:end gh/states))))
                         [(animated/set scale-offset (animated/clamp scale min-scale max-scale))
                          (vec/set offset (vec/add offset (vec/multiply local-translation
                                                                        (vec/divide scale-offset scale))))
                          (animated/cond* (animated/not* (animated/eq scale scale-offset))
                                          (animated/set scale (animated/re-timing {:clock    scale-clock
                                                                                   :from     scale
                                                                                   :duration overscale-duration
                                                                                   :to       scale-offset})))
                          (animated/set should-decay 1)
                          (animated/set (:scale pinch) 1)
                          (vec/set local-translation 0)
                          (vec/set (:focal pinch) 0)])
         (animated/cond* (animated/or* (animated/eq (:state pan) (:active gh/states))
                                       pinch-active)
                         [(animated/stop-clock (.-x clock))
                          (animated/stop-clock (.-y clock))
                          (animated/set should-decay 0)])
         (animated/cond* (animated/and*
                          (animated/neq (animated/diff (:state pan)) 0)
                          (animated/eq (:state pan) (:end gh/states))
                          (animated/not* pinch-active))
                         (animated/set should-decay 1))
         (animated/cond* should-decay
                         (vec/set offset
                                  (vec/clamp
                                   (decay-vector offset (:velocity pan) clock)
                                   min-vec
                                   max-vec)))
         (animated/call* [(animated/clock-running scale-clock)] println)
         (animated/cond* (animated/not* (animated/clock-running scale-clock))
                         (animated/set scale (animated/multiply (:scale pinch) scale-offset)))

         (vec/set translate (vec/add local-translation offset))]))
     [])))

(defn pinch-zoom [props]
  (let [{uri           :uri
         on-close      :onClose
         screen-height :screenHeight
         screen-width  :screenWidth
         width         :width
         height        :height}   (bean/bean props)
        pinch-ref                 (react/create-ref nil)
        pan-ref                   (react/create-ref nil)
        {gesture-handler :gesture-handler
         :as             pinch}   (animated/use-pinch-gesture-handler)
        {pan-gesture-handler :gesture-handler
         :as                 pan} (animated/use-pan-gesture-handler)
        translate                 (vec/create-value 0 0)
        translation               (vec/create-value 0)
        translate-y               (animated/use-value 0)
        scale                     (animated/use-value 1)
        clock                     (animated/use-clock)
        swipe-scale               (animated/interpolate translate-y
                                                        {:inputRange  [0 (* 1.5 screen-height)]
                                                         :outputRange [1 0]
                                                         :extrapolate (:clamp animated/extrapolate)})
        canvas                    (vec/create screen-width screen-height)
        norm-scale                (animated/sub scale 1)
        min-x                     (animated/cond* (animated/greater (animated/multiply width norm-scale) screen-width)
                                                  (animated/sub screen-width (animated/multiply width norm-scale)))
        min-y                     (animated/cond* (animated/greater (animated/multiply height norm-scale) screen-height)
                                                  (animated/sub screen-height (animated/multiply height norm-scale)))
        min-offset-vec            (vec/create min-x min-y)
        close-at                  (animated/multiply (.-y canvas) swipe-close-at)
        snap-to                   (animated/snap-point translate-y
                                                       (.-y (:velocity pan))
                                                       [0 close-at])]

    (use-pinch pinch pan translate translation scale min-offset-vec canvas)
    (animated/code!
     (fn []
       (animated/block
        [(animated/on-change
          (.-y translation)
          (animated/cond* (animated/eq (:state pan) (:active gh/states))
                          [(animated/set translate-y (animated/clamp (.-y translation) 0 screen-height))]))
         (animated/cond* (animated/and* (animated/eq (:state pan) (:end gh/states))
                                        (animated/neq (.-y translation) 0))
                         [(animated/set translate-y (animated/re-timing {:clock    clock
                                                                         :from     translate-y
                                                                         :duration snap-duration
                                                                         :to       snap-to}))
                          (animated/cond* (animated/and* (animated/not* (animated/clock-running clock))
                                                         (animated/eq translate-y close-at))
                                          [(animated/call* [] on-close)])])]))
     [on-close])

    (reagent/as-element
     [animated/view {:style {:align-items     :center
                             :justify-content :center
                             :flex            1
                             :width           screen-width
                             :height          screen-height}}
      [animated/view {:style {:position         "absolute"
                              :top              0
                              :bottom           0
                              :left             0
                              :right            0
                              :opacity          (animated/interpolate translate-y
                                                                      {:inputRange  [0 (* screen-height 0.25)]
                                                                       :outputRange [1 0]
                                                                       :extrapolate (:clamp animated/extrapolate)})
                              :background-color :black}}]
      [gh/pan-gesture-handler (merge {:ref                  pan-ref
                                      :min-dist             10
                                      :avg-touches          true
                                      :simultaneousHandlers pinch-ref}
                                     pan-gesture-handler)
       [animated/view {:style {:position "absolute"
                               :top      0
                               :bottom   0
                               :left     0
                               :right    0}}
        [gh/pinch-gesture-handler (merge {:ref                  pinch-ref
                                          :simultaneousHandlers pan-ref}
                                         gesture-handler)
         [animated/view {:style {:position        "absolute"
                                 :top             0
                                 :bottom          0
                                 :left            0
                                 :right           0
                                 :justify-content :center
                                 :align-items     :center}}
          (when (string? uri)
            [animated/image {:source {:uri uri}
                             :style  {:resize-mode "contain"
                                      :width       width
                                      :height      height
                                      :transform   [{:translateX (.-x translate)}
                                                    {:translateY (.-y translate)}
                                                    {:scale scale}
                                                    ;; Swipe to dismiss transformations
                                                    {:translateY (animated/multiply translate-y
                                                                                    (animated/sub 2 swipe-scale))}
                                                    {:scale swipe-scale}]}}])]]]]])))
