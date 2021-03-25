(ns status-im.ui.screens.chat.components.input
  (:require [status-im.ui.components.icons.icons :as icons]
            [quo.react-native :as rn]
            [quo.react :as react]
            [quo.platform :as platform]
            [quo.components.text :as text]
            [quo.design-system.colors :as colors]
            [status-im.ui.screens.chat.components.style :as styles]
            [status-im.ui.screens.chat.components.reply :as reply]
            [status-im.chat.constants :as chat.constants]
            [status-im.utils.utils :as utils.utils]
            [quo.components.animated.pressable :as pressable]
            [re-frame.core :as re-frame]
            [status-im.i18n.i18n :as i18n]
            [status-im.chat.models.mentions :as mentions]
            [status-im.ui.components.list.views :as list]
            [quo.components.list.item :as list-item]
            [status-im.ui.screens.chat.photos :as photos]
            [reagent.core :as reagent]
            [clojure.string :as string]))

(defn input-focus [text-input-ref]
  (some-> ^js (react/current-ref text-input-ref) .focus))

(def panel->icons {:extensions :main-icons/commands
                   :images     :main-icons/photo})

(defn touchable-icon [{:keys [panel active set-active accessibility-label]}]
  [pressable/pressable {:type                :scale
                        :accessibility-label accessibility-label
                        :on-press            #(set-active (when-not (= active panel) panel))}
   [rn/view {:style (styles/touchable-icon)}
    [icons/icon
     (panel->icons panel)
     (styles/icon (= active panel))]]])

(defn touchable-stickers-icon [{:keys [panel active set-active accessibility-label input-focus]}]
  [pressable/pressable {:type                :scale
                        :accessibility-label accessibility-label
                        :on-press            #(if (= active panel)
                                                (input-focus)
                                                (set-active panel))}
   [rn/view {:style (styles/in-input-touchable-icon)}
    (if (= active panel)
      [icons/icon :main-icons/keyboard (styles/icon false)]
      [icons/icon :main-icons/stickers (styles/icon false)])]])

;; TODO(Ferossgp): Move this into audio panel.
;; Instead of not changing panel we can show a placeholder with no permission
(defn- request-record-audio-permission [set-active panel]
  (re-frame/dispatch
   [:request-permissions
    {:permissions [:record-audio]
     :on-allowed
     #(set-active panel)
     :on-denied
     #(utils.utils/set-timeout
       (fn []
         (utils.utils/show-popup (i18n/label :t/audio-recorder-error)
                                 (i18n/label :t/audio-recorder-permissions-error)))
       50)}]))

(defn touchable-audio-icon [{:keys [panel active set-active accessibility-label input-focus]}]
  [pressable/pressable {:type                :scale
                        :accessibility-label accessibility-label
                        :on-press            #(if (= active panel)
                                                (input-focus)
                                                (request-record-audio-permission set-active panel))}
   [rn/view {:style (styles/in-input-touchable-icon)}
    (if (= active panel)
      [icons/icon :main-icons/keyboard (styles/icon false)]
      [icons/icon :main-icons/speech (styles/icon false)])]])

(defn send-button [on-send]
  [rn/touchable-opacity {:on-press-in on-send}
   [rn/view {:style (styles/send-message-button)}
    [icons/icon :main-icons/arrow-up
     {:container-style     (styles/send-message-container)
      :accessibility-label :send-message-button
      :color               (styles/send-icon-color)}]]])

(defn on-selection-change [timeout-id last-text-change mentionable-users args]
  (let [selection (.-selection ^js (.-nativeEvent ^js args))
        start (.-start selection)
        end (.-end selection)]
    ;; NOTE(rasom): on iOS we do not dispatch this event immediately
    ;; because it is needed only in case if selection is changed without
    ;; typing. Timeout might be canceled on `on-change`.
    (when platform/ios?
      (reset!
       timeout-id
       (utils.utils/set-timeout
        #(re-frame/dispatch [::mentions/on-selection-change
                             {:start start
                              :end   end}
                             mentionable-users])
        50)))
    ;; NOTE(rasom): on Android we dispatch event only in case if there
    ;; was no text changes during last 50ms. `on-selection-change` is
    ;; dispatched after `on-change`, that's why there is no another way
    ;; to know whether selection was changed without typing.
    (when (and platform/android?
               (or (not @last-text-change)
                   (< 50 (- (js/Date.now) @last-text-change))))
      (re-frame/dispatch [::mentions/on-selection-change
                          {:start start
                           :end   end}
                          mentionable-users]))))

(defonce input-texts (atom {}))
(defonce mentions-enabled (reagent/atom {}))

(defn show-send [{:keys [actions-ref send-ref sticker-ref]}]
  (quo.react/set-native-props actions-ref #js {:width 0 :left -88})
  (quo.react/set-native-props send-ref #js {:width nil :right nil})
  (quo.react/set-native-props sticker-ref #js {:width 0 :right -100}))

(defn hide-send [{:keys [actions-ref send-ref sticker-ref]}]
  (quo.react/set-native-props actions-ref #js {:width nil :left nil})
  (quo.react/set-native-props send-ref #js {:width 0 :right -100})
  (quo.react/set-native-props sticker-ref #js {:width nil :right nil}))

(defn reset-input [refs chat-id]
  (some-> ^js (react/current-ref (:text-input-ref refs)) .clear)
  (swap! mentions-enabled update :render not)
  (swap! input-texts dissoc chat-id))

(defn clear-input [chat-id refs]
  (hide-send refs)
  (if (get @mentions-enabled chat-id)
    (do
      (swap! mentions-enabled dissoc chat-id)
      ;;we need this timeout, because if we clear text input and first index was a mention object with blue color,
      ;;after clearing text will be typed with this blue color, so we render white text first and then clear it
      (js/setTimeout #(reset-input refs chat-id) 50))
    (reset-input refs chat-id)))

(defn on-text-change [val chat-id]
  (swap! input-texts assoc chat-id val)
  ;;we still store it in app-db for mentions, we don't have reactions in views
  (re-frame/dispatch [:chat.ui/set-chat-input-text val]))

(defn on-change [last-text-change timeout-id mentionable-users refs chat-id sending-image args]
  (let [text (.-text ^js (.-nativeEvent ^js args))
        prev-text (get @input-texts chat-id)]
    (when (and (seq prev-text) (empty? text) (not sending-image))
      (hide-send refs))
    (when (and (empty? prev-text) (seq text))
      (show-send refs))

    (when (and (not (get @mentions-enabled chat-id)) (string/index-of text "@"))
      (swap! mentions-enabled assoc chat-id true))

    ;; NOTE(rasom): on iOS `on-selection-change` is canceled in case if it
    ;; happens during typing because it is not needed for mention
    ;; suggestions calculation
    (when (and platform/ios? @timeout-id)
      (utils.utils/clear-timeout @timeout-id))
    (when platform/android?
      (reset! last-text-change (js/Date.now)))

    (on-text-change text chat-id)
    ;; NOTE(rasom): on iOS `on-change` is dispatched after `on-text-input`,
    ;; that's why mention suggestions are calculated on `on-change`
    (when platform/ios?
      (re-frame/dispatch [::mentions/calculate-suggestions mentionable-users]))))

(defn on-text-input [mentionable-users chat-id args]
  (let [native-event (.-nativeEvent ^js args)
        text (.-text ^js native-event)
        previous-text (.-previousText ^js native-event)
        range (.-range ^js native-event)
        start (.-start ^js range)
        end (.-end ^js range)]
    (when (and (not (get @mentions-enabled chat-id)) (string/index-of text "@"))
      (swap! mentions-enabled assoc chat-id true))

    (re-frame/dispatch
     [::mentions/on-text-input
      {:new-text      text
       :previous-text previous-text
       :start         start
       :end           end}])
    ;; NOTE(rasom): on Android `on-text-input` is dispatched after
    ;; `on-change`, that's why mention suggestions are calculated
    ;; on `on-change`
    (when platform/android?
      (re-frame/dispatch [::mentions/calculate-suggestions mentionable-users]))))

(defn text-input [{:keys [set-active-panel refs chat-id sending-image]}]
  (let [cooldown-enabled? @(re-frame/subscribe [:chats/cooldown-enabled?])
        mentionable-users @(re-frame/subscribe [:chats/mentionable-users])
        timeout-id (atom nil)
        last-text-change (atom nil)
        mentions-enabled (get @mentions-enabled chat-id)]
    [rn/text-input
     {:style                    (styles/text-input)
      :ref                      (:text-input-ref refs)
      :max-font-size-multiplier 1
      :accessibility-label      :chat-message-input
      :text-align-vertical      :center
      :multiline                true
      :editable                 (not cooldown-enabled?)
      :blur-on-submit           false
      :auto-focus               false
      :on-focus                 #(set-active-panel nil)
      :max-length               chat.constants/max-text-size
      :placeholder-text-color   (:text-02 @colors/theme)
      :placeholder              (if cooldown-enabled?
                                  (i18n/label :cooldown/text-input-disabled)
                                  (i18n/label :t/type-a-message))
      :underline-color-android  :transparent
      :auto-capitalize          :sentences
      :on-selection-change      (partial on-selection-change timeout-id last-text-change mentionable-users)
      :on-change                (partial on-change last-text-change timeout-id mentionable-users refs chat-id sending-image)
      :on-text-input            (partial on-text-input mentionable-users chat-id)}
     (if mentions-enabled
       (for [[idx [type text]] (map-indexed
                                (fn [idx item]
                                  [idx item])
                                @(re-frame/subscribe [:chat/input-with-mentions]))]
         ^{:key (str idx "_" type "_" text)}
         [rn/text (when (= type :mention) {:style {:color "#0DA4C9"}})
          text])
       (get @input-texts chat-id))]))

(defn mention-item
  [[public-key {:keys [alias name nickname] :as user}] _ _ text-input-ref]
  (let [ens-name? (not= alias name)]
    [list-item/list-item
     (cond-> {:icon              [photos/member-photo public-key]
              :size              :small
              :text-size         :small
              :title
              [text/text
               {:weight          :medium
                :ellipsize-mode  :tail
                :number-of-lines 1
                :size            :small}
               (if nickname
                 nickname
                 name)
               (when nickname
                 [text/text
                  {:weight         :regular
                   :color          :secondary
                   :ellipsize-mode :tail
                   :size           :small}
                  " "
                  (when ens-name?
                    "@")
                  name])]
              :title-text-weight :medium
              :on-press
              (fn []
                (re-frame/dispatch [:chat.ui/select-mention text-input-ref user]))}

       ens-name?
       (assoc :subtitle alias))]))

(def chat-toolbar-height (reagent/atom nil))

(defn autocomplete-mentions [text-input-ref bottom]
  (let [suggestions @(re-frame/subscribe [:chat/mention-suggestions])]
    (when (seq suggestions)
      (let [height (+ 16 (* 52 (min 4.5 (count suggestions))))]
        [rn/view
         {:style               (styles/autocomplete-container bottom)
          :accessibility-label :suggestions-list}
         [rn/view
          {:style {:height height}}
          [list/flat-list
           {:keyboardShouldPersistTaps :always
            :footer                    [rn/view {:style {:height 8}}]
            :header                    [rn/view {:style {:height 8}}]
            :data                      suggestions
            :key-fn                    first
            :render-data               text-input-ref
            :render-fn                 mention-item}]]]))))

(defn on-chat-toolbar-layout [^js ev]
  (reset! chat-toolbar-height (-> ev .-nativeEvent .-layout .-height)))

(defn focus-input-on-reply [reply had-reply text-input-ref]
  ;;when we show reply we focus input
  (when-not (= reply @had-reply)
    (reset! had-reply reply)
    (when reply
      (js/setTimeout #(input-focus text-input-ref) 250))))

(defn reply-message [text-input-ref]
  (let [had-reply (atom nil)]
    (fn []
      (let [reply @(re-frame/subscribe [:chats/reply-message])]
        (focus-input-on-reply reply had-reply text-input-ref)
        (when reply
          [reply/reply-message reply])))))

(defn send-image []
  (let [sending-image @(re-frame/subscribe [:chats/sending-image])]
    (when (seq sending-image)
      [reply/send-image sending-image])))

(defn actions [extensions image show-send actions-ref active-panel set-active-panel]
  [rn/view {:style (styles/actions-wrapper show-send)
            :ref   actions-ref}
   (when extensions
     [touchable-icon {:panel               :extensions
                      :accessibility-label :show-extensions-icon
                      :active              active-panel
                      :set-active          set-active-panel}])
   (when image
     [touchable-icon {:panel               :images
                      :accessibility-label :show-photo-icon
                      :active              active-panel
                      :set-active          set-active-panel}])])

(defn chat-toolbar []

  (let [previous-layout          (atom nil)
        had-reply                (atom nil)]
    (fn [{:keys [active-panel set-active-panel text-input-ref on-text-change]}]
      (let [disconnected?        @(re-frame/subscribe [:disconnected?])
            {:keys [processing]} @(re-frame/subscribe [:multiaccounts/login])
            mainnet?             @(re-frame/subscribe [:mainnet?])
            input-text
            @(re-frame/subscribe [:chats/current-chat-input-text])
            input-with-mentions
            @(re-frame/subscribe [:chat/input-with-mentions])
            cooldown-enabled?    @(re-frame/subscribe [:chats/cooldown-enabled?])
            one-to-one-chat?     @(re-frame/subscribe [:current-chat/one-to-one-chat?])
            {:keys [public?
                    chat-id]}    @(re-frame/subscribe [:current-chat/metadata])
            reply                @(re-frame/subscribe [:chats/reply-message])
            sending-image        @(re-frame/subscribe [:chats/sending-image])
            input-focus          (fn []
                                   (some-> ^js (react/current-ref text-input-ref) .focus))
            clear-input          (fn []
                                   (some-> ^js (react/current-ref text-input-ref) .clear))
            empty-text           (string/blank? (string/trim (or input-text "")))
            show-send            (and (or (not empty-text)
                                          sending-image)
                                      (not (or processing disconnected?)))
            show-stickers        (and empty-text
                                      mainnet?
                                      (not sending-image)
                                      (not reply))
            show-image           (and empty-text
                                      (not reply))
                                      
            show-extensions      (and empty-text
            
                                      (or config/commands-enabled? mainnet?)
                                      (not reply))
            show-audio           (and empty-text
                                      (not sending-image)
                                      (not reply))]
                                      
        (when-not (= reply @had-reply)
          (reset! had-reply reply)
          (when reply
            (js/setTimeout input-focus 250)))
        (when (and platform/ios? (not= @previous-layout [show-send show-stickers show-extensions show-audio]))
          (reset! previous-layout [show-send show-stickers show-extensions show-audio])
          (when (seq @previous-layout)
            (rn/configure-next
             (:ease-opacity-200 rn/custom-animations))))
        [chat-input {:set-active-panel         set-active-panel
                     :active-panel             active-panel
                     :text-input-ref           text-input-ref
                     :input-focus              input-focus
                     :reply                    reply
                     :on-send-press            #(do (re-frame/dispatch [:chat.ui/send-current-message])
                                                    (clear-input))
                     :text-value               input-text
                     :input-with-mentions      input-with-mentions
                     :on-text-change           on-text-change
                     :cooldown-enabled?        cooldown-enabled?
                     :show-send                show-send
                     :show-stickers            show-stickers
                     :show-image               show-image
                     :show-audio               show-audio
                     :sending-image            sending-image
                     :show-extensions          show-extensions
                     :chat-id                  chat-id}]))))

  (let [actions-ref (quo.react/create-ref)
        send-ref (quo.react/create-ref)
        sticker-ref (quo.react/create-ref)
        toolbar-options (re-frame/subscribe [:chats/chat-toolbar])]
    (fn [{:keys [active-panel set-active-panel text-input-ref chat-id]}]
      (let [;we want to control components on native level, so instead of RN state we set native props via reference
            ;we don't react on input text in this view, @input-texts below is a regular atom
            refs {:actions-ref    actions-ref
                  :send-ref       send-ref
                  :sticker-ref    sticker-ref
                  :text-input-ref text-input-ref}
            {:keys [send stickers image extensions audio sending-image]} @toolbar-options
            show-send (or sending-image (seq (get @input-texts chat-id)))]
        [rn/view {:style     (styles/toolbar)
                  :on-layout on-chat-toolbar-layout}
         ;;EXTENSIONS and IMAGE buttons
         [actions extensions image show-send actions-ref active-panel set-active-panel]
         [rn/view {:style (styles/input-container)}
          [reply-message text-input-ref]
          [send-image]
          [rn/view {:style styles/input-row}
           [text-input {:chat-id          chat-id
                        :sending-image    sending-image
                        :refs             refs
                        :set-active-panel set-active-panel}]
           ;;SEND button
           [rn/view {:ref send-ref :style (when-not show-send {:width 0 :right -100})}
            (when send
              [send-button #(do (clear-input chat-id refs)
                                (re-frame/dispatch [:chat.ui/send-current-message]))])]

           ;;STICKERS and AUDIO buttons
           [rn/view {:style (merge {:flex-direction :row} (when show-send {:width 0 :right -100}))
                     :ref   sticker-ref}
            (when stickers
              [touchable-stickers-icon {:panel               :stickers
                                        :accessibility-label :show-stickers-icon
                                        :active              active-panel
                                        :input-focus         #(input-focus text-input-ref)
                                        :set-active          set-active-panel}])
            (when audio
              [touchable-audio-icon {:panel               :audio
                                     :accessibility-label :show-audio-message-icon
                                     :active              active-panel
                                     :input-focus         #(input-focus text-input-ref)
                                     :set-active          set-active-panel}])]]]]))))

