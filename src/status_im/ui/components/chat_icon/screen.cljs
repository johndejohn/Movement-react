(ns status-im.ui.components.chat-icon.screen
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame.core]
            [status-im.multiaccounts.core :as multiaccounts]
            [status-im.ui.components.icons.icons :as icons]
            [status-im.ui.components.chat-icon.styles :as styles]
            [quo.design-system.colors :as colors]
            [status-im.ui.components.react :as react]
            [status-im.ui.screens.chat.photos :as photos]
            [status-im.profile.db :as profile.db]
            [status-im.ui.screens.profile.visibility-status.utils :as visibility-status-utils]))

;;TODO REWORK THIS NAMESPACE

(def get-name-first-char
  (memoize
   (fn [name]
     ;; TODO: for now we check if the first letter is a #
     ;; which means it is most likely a public chat and
     ;; use the second letter if that is the case
     ;; a broader refactoring should clean up upstream params
     ;; for default-chat-icon
     (string/capitalize (if (and (= "#" (first name))
                                 (< 1 (count name)))
                          (second name)
                          (first name))))))

(defn default-chat-icon [name styles]
  (when-not (string/blank? name)
    [react/view (:default-chat-icon styles)
     [react/text {:style (:default-chat-icon-text styles)}
      (get-name-first-char name)]]))

(defn chat-icon-view
  [chat-id group-chat name styles]
  [react/view (:container styles)
   (if group-chat
     [default-chat-icon name styles]
     (let [photo-path @(re-frame.core/subscribe [:chats/photo-path chat-id])]
       [photos/photo photo-path styles]))])

(defn emoji-chat-icon [emoji styles]
  (when-not (string/blank? emoji)
    [react/view (:default-chat-icon styles)
     [react/text {:style (:default-chat-icon-text styles)} emoji]]))

(defn profile-photo-plus-dot-view
  [{:keys [public-key photo-container photo-path community?]}]
  (let [photo-path      (if (nil? photo-path)
                          @(re-frame.core/subscribe [:chats/photo-path public-key])
                          photo-path)
        photo-container (if (nil? photo-container)
                          styles/container-chat-list photo-container)
        size            (:width photo-container)
        identicon?      (when photo-path (profile.db/base64-png? photo-path))
        dot-styles      (visibility-status-utils/icon-visibility-status-dot
                         public-key size identicon?)]
    [react/view {:style               photo-container
                 :accessibility-label :profile-photo}
     [photos/photo photo-path {:size size}]
     (when-not community?
       [react/view {:style               dot-styles
                    :accessibility-label :profile-photo-dot}])]))

(defn emoji-chat-icon-view
  [chat-id group-chat name emoji styles]
  [react/view (:container styles)
   (if group-chat
     (if (string/blank? emoji)
       [default-chat-icon name styles]
       [emoji-chat-icon emoji styles])
     [profile-photo-plus-dot-view {:public-key      chat-id
                                   :photo-container (:default-chat-icon styles)}])])

(defn chat-icon-view-toolbar
  [chat-id group-chat name color emoji]
  [emoji-chat-icon-view chat-id group-chat name emoji
   {:container              styles/container-chat-toolbar
    :size                   36
    :chat-icon              styles/chat-icon-chat-toolbar
    :default-chat-icon      (styles/default-chat-icon-chat-toolbar color)
    :default-chat-icon-text (if (string/blank? emoji)
                              (styles/default-chat-icon-text 36)
                              (styles/emoji-chat-icon-text 36))}])

(defn chat-icon-view-chat-list
  [chat-id group-chat name color]
  [chat-icon-view chat-id group-chat name
   {:container              styles/container-chat-list
    :size                   40
    :chat-icon              styles/chat-icon-chat-list
    :default-chat-icon      (styles/default-chat-icon-chat-list color)
    :default-chat-icon-text (styles/default-chat-icon-text 40)}])

(defn chat-icon-view-chat-sheet
  [chat-id group-chat name color]
  [chat-icon-view chat-id group-chat name
   {:container              styles/container-chat-list
    :size                   40
    :chat-icon              styles/chat-icon-chat-list
    :default-chat-icon      (styles/default-chat-icon-chat-list color)
    :default-chat-icon-text (styles/default-chat-icon-text 40)}])

(defn emoji-chat-icon-view-chat-sheet
  [chat-id group-chat name color emoji]
  [emoji-chat-icon-view chat-id group-chat name emoji
   {:container              styles/container-chat-list
    :size                   40
    :chat-icon              styles/chat-icon-chat-list
    :default-chat-icon      (styles/default-chat-icon-chat-list color)
    :default-chat-icon-text (if (string/blank? emoji)
                              (styles/default-chat-icon-text 40)
                              (styles/emoji-chat-icon-text 40))}])

(defn custom-icon-view-list
  [name color & [size]]
  [react/view (styles/container-list-size (or size 40))
   [default-chat-icon name {:default-chat-icon      (styles/default-chat-icon-profile color (or size 40))
                            :default-chat-icon-text (styles/default-chat-icon-text (or size 40))}]])

(defn contact-icon-view
  [contact {:keys [container] :as styles}]
  [react/view container
   [photos/photo (multiaccounts/displayed-photo contact) styles]])

(defn contact-icon-contacts-tab [photo-path]
  [react/view  styles/container-chat-list
   [photos/photo photo-path {:size 40}]])

(defn dapp-icon-permission [contact size]
  [contact-icon-view contact
   {:container              {:width size :height size}
    :size                   size
    :chat-icon              (styles/custom-size-icon size)
    :default-chat-icon      (styles/default-chat-icon-profile colors/default-chat-color size)
    :default-chat-icon-text (styles/default-chat-icon-text size)}])

(defn chat-intro-icon-view [icon-text chat-id group-chat styles]
  (if group-chat
    [default-chat-icon icon-text styles]
    (let [photo-path @(re-frame.core/subscribe [:chats/photo-path chat-id])]
      (if-not (string/blank? photo-path)
        [photos/photo photo-path styles]))))

(defn emoji-chat-intro-icon-view [icon-text chat-id group-chat emoji styles]
  (if group-chat
    (if (string/blank? emoji)
      [default-chat-icon icon-text styles]
      [emoji-chat-icon emoji styles])
    (let [photo-path @(re-frame.core/subscribe [:chats/photo-path chat-id])]
      (if-not (string/blank? photo-path)
        [photos/photo photo-path styles]))))

(defn profile-icon-view
  [photo-path name color emoji edit? size override-styles public-key community?]
  (let [styles (merge {:container              {:width size :height size}
                       :size                   size
                       :chat-icon              styles/chat-icon-profile
                       :default-chat-icon      (styles/default-chat-icon-profile color size)
                       :default-chat-icon-text (if (string/blank? emoji)
                                                 (styles/default-chat-icon-text size)
                                                 (styles/emoji-chat-icon-text size))} override-styles)]
    [react/view (:container styles)
     (if (and photo-path (seq photo-path))
       [profile-photo-plus-dot-view {:photo-path      photo-path
                                     :public-key      public-key
                                     :photo-container (:container styles)
                                     :community?      community?}]
       (if (string/blank? emoji)
         [default-chat-icon name styles]
         [emoji-chat-icon emoji styles]))
     (when edit?
       [react/view {:style (styles/chat-icon-profile-edit)}
        [icons/tiny-icon :tiny-icons/tiny-edit {:color colors/white-persist}]])]))
