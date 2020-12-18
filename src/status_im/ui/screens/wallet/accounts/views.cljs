(ns status-im.ui.screens.wallet.accounts.views
  (:require [quo.animated :as reanimated]
            [quo.core :as quo]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [status-im.i18n :as i18n]
            [status-im.ui.screens.wallet.events :as wallet.events]
            [status-im.ui.components.buy-crypto :as buy-crypto]
            [status-im.ui.components.chat-icon.screen :as chat-icon]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.icons.vector-icons :as icons]
            [status-im.ui.components.list.views :as list]
            [status-im.ui.components.react :as react]
            [status-im.ui.screens.wallet.accounts.sheets :as sheets]
            [status-im.ui.screens.wallet.accounts.styles :as styles]
            [status-im.qr-scanner.core :as qr-scanner]
            [status-im.wallet.utils :as wallet.utils]
            [status-im.keycard.login :as keycard.login])
  (:require-macros [status-im.utils.views :as views]))

(def crypto-onramp-link "https://dap.ps/discover-dapps/categories/CRYPTO_ONRAMPS")

(views/defview account-card [{:keys [name color address type] :as account} keycard? card-width]
  (views/letsubs [currency        [:wallet/currency]
                  portfolio-value [:account-portfolio-value address]
                  prices-loading? [:prices-loading?]]
    [react/touchable-highlight
     {:on-press            #(re-frame/dispatch [:navigate-to :wallet-account account])
      :accessibility-label (str "accountcard" name)}
     [react/view {:style (styles/card color card-width)}
      [react/view {:flex-direction :row :align-items :center :justify-content :space-between}
       [react/text {:style styles/card-name} name]
       [quo/text styles/card-address
        address]]
      [react/view {:flex-direction :row :align-items :center :justify-content :space-between}
       [react/view {:style {:flex-direction :row}}
        (if prices-loading?
          [react/small-loading-indicator :colors/white-persist]
          [react/text {:style styles/card-value
                       :accessibility-label "account-total-value"} portfolio-value])
        [react/text {:style styles/card-value-currency} (str " " (:code currency))]]
       (let [icon (cond
                    (= type :watch)
                    :main-icons/show

                    (and (not= type :watch) keycard?)
                    :main-icons/keycard-account)]
         (when icon
           [icons/icon icon {:container-style styles/card-icon-type
                             :color color}]))
       [react/touchable-highlight
        {:style styles/card-icon-more
         :on-press #(re-frame/dispatch [:bottom-sheet/show-sheet
                                        {:content        (fn [] [sheets/account-card-actions account type])
                                         :content-height 130}])}
        [icons/icon :main-icons/more {:color colors/white-persist}]]]]]))

(defn add-card [card-width]
  [react/touchable-highlight {:on-press            #(re-frame/dispatch [:bottom-sheet/show-sheet
                                                                        {:content        sheets/add-account
                                                                         :content-height 260}])
                              :accessibility-label "add-new-account"}
   [react/view {:style (styles/add-card card-width)}
    [icons/icon :main-icons/add-circle {:color colors/blue}]
    [react/text {:style styles/add-text} (i18n/label :t/add-account)]]])

(defn render-asset [{:keys [icon decimals amount color value] :as token} _ _ currency]
  [quo/list-item
   {:title               [quo/text {:weight :medium}
                          [quo/text {:weight :inherit}
                           (str (if amount
                                  (wallet.utils/format-amount amount decimals)
                                  "...")
                                " ")]
                          [quo/text {:color  :secondary
                                     :weight :inherit}
                           (wallet.utils/display-symbol token)]]
    :subtitle            (str (if value value "...") " " currency)
    :accessibility-label (str (:symbol token)  "-asset-value")
    :icon                (if icon
                           [list/item-image icon]
                           [chat-icon/custom-icon-view-list (:name token) color])}])

(views/defview assets []
  (views/letsubs [{:keys [tokens]} [:wallet/all-visible-assets-with-values]
                  currency [:wallet/currency]]
    [list/flat-list {:data               tokens
                     :default-separator? false
                     :key-fn             :name
                     :render-data        (:code currency)
                     :render-fn          render-asset}]))

(views/defview send-button []
  (views/letsubs [account [:multiaccount/default-account]]
    [react/view styles/send-button-container
     [quo/button
      {:accessibility-label :send-transaction-button
       :type                :scale
       :on-press            #(re-frame/dispatch [:wallet/prepare-transaction-from-wallet account])}
      [react/view (styles/send-button)
       [icons/icon :main-icons/send {:color colors/white-persist}]]]]))

(defn dot []
  (fn [{:keys [selected]}]
    [react/view {:style (styles/dot-style selected)}]))

(defn dots-selector [{:keys [n selected]}]
  [react/view {:style (styles/dot-selector)}
   (for [i (range n)]
     ^{:key i}
     [dot {:selected (= selected i)}])])

(views/defview accounts []
  (views/letsubs [accounts [:multiaccount/accounts]
                  keycard? [:keycard-multiaccount?]
                  window-width [:dimensions/window-width]
                  index (reagent/atom 0)]
    (let [card-width (quot window-width 1.1)
          page-width (styles/page-width card-width)]
      [react/view {:style {:align-items     :center
                           :flex            1
                           :justify-content :flex-end}}
       [react/scroll-view {:horizontal                        true
                           :deceleration-rate                 "fast"
                           :snap-to-interval                  page-width
                           :snap-to-alignment                 "left"
                           :shows-horizontal-scroll-indicator false
                           :scroll-event-throttle             64
                           :on-scroll                         #(let [x (.-nativeEvent.contentOffset.x ^js %)]
                                                                 (reset! index (Math/max (Math/round (/ x page-width)) 0)))}
        [react/view styles/dot-container
         (doall
          (for [account accounts]
            ^{:key account}
            [account-card account keycard? card-width]))
         [add-card card-width]]]
       (let [columns (Math/ceil (/ (inc (count accounts)) 2))
             totalwidth  (* (styles/page-width card-width) columns)
             n (Math/ceil (/ totalwidth window-width))]
         (when (> n 1)
           [dots-selector {:selected @index
                           :n        n}]))])))

(views/defview total-value [{:keys [animation minimized]}]
  (views/letsubs [currency           [:wallet/currency]
                  portfolio-value    [:portfolio-value]
                  empty-balances?    [:empty-balances?]
                  frozen-card?       [:keycard/frozen-card?]
                  {:keys [mnemonic]} [:multiaccount]]
    [reanimated/view {:style (styles/container {:minimized minimized})}
     (when (or
            (and frozen-card? minimized)
            (and mnemonic minimized (not empty-balances?)))
       [reanimated/view {:style (styles/accounts-mnemonic {:animation animation})}
        [react/touchable-highlight
         {:on-press #(re-frame/dispatch
                      (if frozen-card?
                        [::keycard.login/reset-pin]
                        [:navigate-to :profile-stack {:screen :backup-seed
                                                      :initial false}]))}
         [react/view {:flex-direction :row
                      :align-items    :center}
          [react/view {:width            14
                       :height           14
                       :background-color colors/gray
                       :border-radius    7
                       :align-items      :center
                       :justify-content  :center
                       :margin-right     9}
           [react/text {:style {:color       colors/white
                                :font-size   13
                                :font-weight "700"}}
            "!"]]
          [react/text {:style               {:color colors/gray}
                       :accessibility-label :back-up-your-seed-phrase-warning}
           (if frozen-card?
             (i18n/label :t/your-card-is-frozen)
             (i18n/label :t/back-up-your-seed-phrase))]]]])

     [reanimated/view {:style (styles/value-container {:minimized minimized
                                                       :animation animation})
                       :pointer-events :none}
      [reanimated/view {:style {:justify-content :center}}
       [quo/text {:animated? true
                  :weight    :semi-bold
                  :style     (styles/value-text {:minimized minimized})}
        portfolio-value
        [quo/text {:animated? true
                   :size      :inherit
                   :weight    :inherit
                   :color     :secondary}
         (str " " (:code currency))]]]]
     (when-not minimized
       [reanimated/view
        [quo/text {:color :secondary}
         (i18n/label :t/wallet-total-value)]])]))

(defn buy-crypto []
  (let [empty-balances    @(re-frame/subscribe [:empty-balances?])
        buy-crypto-hidden @(re-frame/subscribe [:wallet/buy-crypto-hidden])]
    (when (and empty-balances
               (not buy-crypto-hidden))
      [react/view {:style {:padding-horizontal 8
                           :padding-top        24}}
       [buy-crypto/banner {:on-open  #(re-frame/dispatch [:browser.ui/open-url crypto-onramp-link])
                           :on-close #(re-frame/dispatch [::wallet.events/hide-buy-crypto])}]])))

(defn accounts-overview []
  (fn []
    (let [mnemonic @(re-frame/subscribe [:mnemonic])]
      [react/view {:flex 1}
       [quo/animated-header
        {:extended-header   total-value
         :use-insets        true
         :right-accessories [{:on-press            #(re-frame/dispatch
                                                     [::qr-scanner/scan-code
                                                      {:handler :wallet.send/qr-scanner-result}])
                              :icon                :main-icons/qr
                              :accessibility-label :accounts-qr-code}
                             {:on-press            #(re-frame/dispatch [:bottom-sheet/show-sheet
                                                                        {:content (sheets/accounts-options mnemonic)}])
                              :icon                :main-icons/more
                              :accessibility-label :accounts-more-options}]}
        [accounts]
        [assets]
        [buy-crypto]
        [react/view {:height 68}]]
       [send-button]])))
