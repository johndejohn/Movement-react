(ns status-im.currency.core-test
  (:require [cljs.test :refer-macros [deftest is]]
            [status-im.currency.core :as models]))

(deftest get-currency
  (is (= :int (models/get-currency {:multiaccount {:currency :int}})))

  (is (= :int (models/get-currency {:multiaccount {:not-empty "would throw an error if was empty"}})))

  (is (= :usd (models/get-currency {:multiaccount {:not-empty "would throw an error if was empty"}})))

  (is (= :aud (models/get-currency {:multiaccount {:currency :aud}}))))

(deftest set-currency
  (let [cofx (models/set-currency {:db {:multiaccount {:not-empty "would throw an error if was empty"}}} :int)]
    (is (= :int (get-in cofx [:db :multiaccount :currency]))))
  (is (= :jpy (get-in (models/set-currency {:db {:multiaccount {:not-empty "would throw an error if was empty"}}} :jpy)
                      [:db :multiaccount :currency]))))
