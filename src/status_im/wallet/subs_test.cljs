(ns status-im.wallet.subs-test
  (:require [cljs.test :refer-macros [deftest is]]
            [status-im.utils.money :as money]
            [status-im.subs :as s]))

(deftest test-balance-total-value
  (is (= (#'status-im.subs/get-balance-total-value
          {:INT (money/bignumber 1000000000000000000)
           :SNT (money/bignumber 100000000000000000000)
           :AST (money/bignumber 10000)}
          {:INT {:USD {:from "INT", :to "USD", :price 677.91, :last-day 658.688}}
           :MNT {:USD {:from "MNT", :to "USD", :price 0.1562, :last-day 0.15}}
           :AST {:USD {:from "AST", :to "USD", :price 4,      :last-day 3}}}
          :USD
          {:INT 18
           :SNT 18
           :AST 4})
         697.53)))
