(ns car-sim.app-test
  (:require [cljs.test :refer [deftest is testing]]
            [re-frame.core :as rf]
            [car-sim.app :as app]))

(deftest default-db-matches-original-scaffold-values
  (testing "app-db holds every literal field the original +page.svelte
            script held, unchanged"
    (is (= "Car Sim C4r51m00" (:page/title app/default-db)))
    (is (= "etzhayyim-project-car-sim" (:page/project app/default-db)))
    (is (= "etzhayyim-wasm-car-sim-c4r51m00" (:page/name app/default-db)))
    (is (= "appview" (:page/kind app/default-db)))
    (is (= 2 (:page/route-count app/default-db)))
    (is (= ["c4r51m00.etzhayyim.com/*" "driver.etzhayyim.com/*"]
           (:page/routes app/default-db)))
    (is (= 10 (count (:page/vars app/default-db))))
    (is (true? (:page/xrpc? app/default-db)))))

(deftest routes-count-matches-routes-vector-length
  (testing "the declared :page/route-count agrees with the actual number of
            :page/routes entries, same as the Svelte source's routeCount"
    (is (= (:page/route-count app/default-db) (count (:page/routes app/default-db))))))

(deftest initialize-db-event-sets-all-subs
  (testing "dispatching :initialize-db makes every reg-sub resolve to
            default-db's value"
    (rf/dispatch-sync [:initialize-db])
    (is (= (:page/title app/default-db) @(rf/subscribe [:page/title])))
    (is (= (:page/project app/default-db) @(rf/subscribe [:page/project])))
    (is (= (:page/name app/default-db) @(rf/subscribe [:page/name])))
    (is (= (:page/kind app/default-db) @(rf/subscribe [:page/kind])))
    (is (= (:page/route-count app/default-db) @(rf/subscribe [:page/route-count])))
    (is (= (:page/routes app/default-db) @(rf/subscribe [:page/routes])))
    (is (= (:page/vars app/default-db) @(rf/subscribe [:page/vars])))
    (is (= (:page/xrpc? app/default-db) @(rf/subscribe [:page/xrpc?])))
    (is (= (:page/relative-path app/default-db) @(rf/subscribe [:page/relative-path])))))

(deftest initialize-db-is-idempotent
  (testing "dispatching :initialize-db twice leaves subs unchanged"
    (rf/dispatch-sync [:initialize-db])
    (rf/dispatch-sync [:initialize-db])
    (is (= (:page/name app/default-db) @(rf/subscribe [:page/name])))))
