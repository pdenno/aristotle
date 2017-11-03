(ns arachne.aristotle.query
  (:require [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query.compiler :as qc]
            [arachne.aristotle.graph :as graph]
            [clojure.spec.alpha :as s])
  (:import [org.apache.jena.query QueryFactory QueryExecutionFactory]
           [org.apache.jena.sparql.algebra AlgebraGenerator Algebra]
           [org.apache.jena.sparql.algebra.op OpProject Op1]))

(s/def ::fn-expr (s/cat :operator symbol? :args (s/* ::expr)))

(s/def ::expr (s/or :fn-expr ::fn-expr
                    :node-expr ::graph/node))

(s/def ::where (s/coll-of (s/or :triples ::graph/triples
                                :filter ::expr)))

(s/def ::select (s/coll-of ::graph/variable :min-count 1))
(s/def ::select-distinct ::select)

(s/def ::query (s/keys :req-un [::where]
                       :opt-un [::select ::select-distinct]))

(defn- compile-query
  "Convert the given query from a conformed Clojure data structure to an ARQ
   Query object, using the specified sequence of compiler passes."
  [data passes]
  (reduce (fn [data pass]
            (pass data))
    data passes))

(s/fdef build
  :args (s/cat :query ::query))

(defn build
  "Build an optimized query from a Clojure data structure. Returns a map
   containing:

   :op - an instance of org.apache.jena.sparql.algebra.Op
   :vars - a sequence of org.apache.jena.sparql.core.Var objects which should
           be bound in the query results.  "
  [query]
  (s/assert* ::query query)
  (compile-query (s/conform ::query query)
    [qc/split-where
     qc/replace-nodes
     qc/replace-exprs
     qc/replace-triples
     qc/bgp
     qc/add-filter
     qc/project
     qc/optimize
     :op]))

(defn- find-vars
  "Unwrap the given operation until we find an OpProject, then return the list of vars."
  [op]
  (cond
    (instance? OpProject op) (.getVars op)
    (instance? Op1 op) (recur (.getSubOp op))
    :else (throw (ex-info (format "Could not extract vars from operation of type %s"
                            (.getClass op)) {:op op}))))

(defn run
  "Given an input dataset (which may be a Graph or a Model) and an Operation,
   evaluate the query and return the results as a realized Clojure data structure."
  [data op]
  (let [vars (find-vars op)]
    (->> (Algebra/exec op data)
      (iterator-seq)
      (map (fn [binding]
               (mapv #(graph/data (.get binding %)) vars)))
      (doall))))

(defn query
  "Build and execute a query on the given dataset."
  [query dataset]
  (let [q (build query)]
    (run dataset q)))

(comment

  (require '[arachne.aristotle.graph :as g])

  (reg/prefix :foaf "http://xmlns.com/foaf/0.1/")
  (reg/prefix :cfg "http://arachne-framework.org/config/")

  (def q '{:select [?name]
           :where [[youngling :foaf/age ?age]
                   (<= 21 ?age)
                   {:rdf/about youngling
                    :foaf/knows {:foaf/name ?name}}]})

  (s/conform ::query q)

  (build q)

  )


(comment

  (def querystr "SELECT DISTINCT ?x ?y
                 WHERE { ?x <http://xmlns.com/foaf/0.1/name> ?y }")
  (def querystr " SELECT ?s { ?s <http://example.com/val> ?val .
                  FILTER ( ?val < 20 ) }")
  (def querystr "PREFIX prop: <http://resedia.org/ontology/>
                 PREFIX res: <http://resedia.org/resource/>
                 PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

                 SELECT DISTINCT ?language ?label
                 WHERE {?country prop:language ?language .
                        ?language rdfs:label ?label .
                 VALUES ?country { res:Spain res:France res:Italy }
                 FILTER langMatches(lang(?label), \"en\")}")


  (def querystr "CONSTRUCT ?name
                 WHERE { ?stmt <http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate> <http://xmlns.com/foaf/0.1/name> .
                         ?stmt <http://www.w3.org/1999/02/22-rdf-syntax-ns#object> ?name .
                         }")

  (def querystr "PREFIX  rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \nPREFIX  foaf:   <http://xmlns.com/foaf/0.1/> \n\nSELECT ?person\nWHERE \n{\n    ?person rdf:type  foaf:Person .\n    FILTER NOT EXISTS { ?person foaf:name ?name }\n}  ")


  (def querystr "SELECT DISTINCT ?x ?y
                 WHERE { ?x <http://xmlns.com/foaf/0.1/name> ?y
                         FILTER NOT EXISTS {?x <http://xmlns.com/foaf/0.1/age> ?age}
                         }")

  (def qq (QueryFactory/create querystr))


  (-> (AlgebraGenerator.)
    (.compile qq)
    ;(.getSubOp)
    ;(.getExprs)
    ;(.getList)
    ;first
    ;(.getArgs)
    )


  (.getResultVars query)

  (def resultmodel (.execConstruct (QueryExecutionFactory/create query model)))


  (with-open [execution (QueryExecutionFactory/create query model)]
    (doseq [soln (iterator-seq (.execSelect execution))]
      (println soln)
      )
    )



  )
