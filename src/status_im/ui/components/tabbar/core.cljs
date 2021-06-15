(ns status-im.ui.components.tabbar.core
  (:require [re-frame.core :as re-frame]
            [status-im.utils.platform :as platform]))


(defn main-tab? [view-id]
  (contains?
   #{:chat-stack :wallet-stack :profile-stack 
     :home :wallet :my-profile :wallet-onboarding-setup}
   view-id))

(def tabs-list-data
  (->>
   [{:nav-stack           :chat-stack
     :content             {:title (i18n/label :t/chat)
                           :icon  :main-icons/message}
     :count-subscription  :chats/unread-messages-number
     :accessibility-label :home-tab-button}

    {:nav-stack           :wallet-stack
     :content             {:title (i18n/label :t/wallet)
                           :icon  :main-icons/wallet}
     :accessibility-label :wallet-tab-button}
    
    {:nav-stack           :profile-stack
     :content             {:title (i18n/label :t/profile)
                           :icon  :main-icons/user-profile}
     :count-subscription  :get-profile-unread-messages-number
     :accessibility-label :profile-tab-button}]
   (remove nil?)
   (map-indexed vector)))

(defn get-height []
  (if platform/android?
    56
    (if platform/iphone-x?
      84
      50)))

(defn chat-tab []
  (let [count-subscription @(re-frame/subscribe [:chats/unread-messages-number])]
    (re-frame/dispatch [:change-tab-count :chat count-subscription])
    nil))


(defn profile-tab []
  (let [count-subscription @(re-frame/subscribe [:get-profile-unread-messages-number])]
    (re-frame/dispatch [:change-tab-count :profile count-subscription])
    nil))

(defn tabs-counts-subscriptions []
  [:<>
   [chat-tab]
   [profile-tab]])