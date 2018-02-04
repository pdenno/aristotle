(ns arachne.aristotle.inference-test
  (:require [clojure.test :refer :all]
            [arachne.aristotle :as aa]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.query :as q]
            [arachne.aristotle :as ar]
            [clojure.java.io :as io]))

(reg/prefix :daml "http://www.daml.org/2001/03/daml+oil#")
(reg/prefix :wo.tf "http://www.workingontologist.org/Examples/Chapter6/TheFirm.owl#")
(reg/prefix :arachne "http://arachne-framework.org/#")

(deftest basic-type-inference
  (let [m (aa/add (aa/model) (graph/load (io/resource "TheFirm.n3")))
        gls #{[:wo.tf/Goldman]
              [:wo.tf/Long]
              [:wo.tf/Spence]}
        withsmith (conj gls [:arachne/Smith])
        ppl-query '[:project [?person]
                    [:bgp
                     [?person :rdf/type :wo.tf/Person]]]
        worksfor-query '[:project [?person]
                         [:bgp
                          [?person :wo.tf/worksFor :wo.tf/TheFirm]]]]
    (is (= gls (set (q/query ppl-query m))))
    (is (= gls (set (q/query worksfor-query m))))
    (aa/add m {:rdf/about :arachne/Smith
               :wo.tf/freeLancesTo :wo.tf/TheFirm})
    (is (= withsmith (set (q/query ppl-query m))))
    (is (= withsmith (set (q/query worksfor-query m))))))


(def pres-props
  [{:rdf/about :wo.tf/president
    :owl/class :owl/FunctionalProperty
    :rdfs/domain :wo.tf/Company
    :rdfs/range :wo.tf/Person
    :owl/inverseOf :wo.tf/presidentOf}
   {:rdf/about :wo.tf/presidentOf
    :rdfs/subPropertyOf :wo.tf/isEmployedBy}
   {:rdf/about :wo.tf/TheFirm
    :wo.tf/president :wo.tf/Flint}])

(deftest inverse-properties
  (let [m (aa/add (aa/model) (graph/load (io/resource "TheFirm.n3")))]
    (aa/add m pres-props)
    (is
     (= [[:wo.tf/TheFirm]]
        (q/query '[:project [?firm]
                   [:bgp
                    [:wo.tf/Flint :wo.tf/worksFor ?firm]]] m)))))
