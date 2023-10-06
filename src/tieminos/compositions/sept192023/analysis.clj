(ns tieminos.compositions.sept192023.analysis
  (:require
   [tieminos.polydori.analysis.dorian-hexanies
    :refer
    [dorian-hex-connections dorian-hexanies-in-polydori-by-name]]))

(def cps
  {:beating-pads "diat2v2"
   :bass "diat4v2"
   :randy-lights "diat6v3"})

;; common tones
(let [all-cps (set (vals cps))]
  (map
    (fn [[k hex-name]]
      [k (filter #(all-cps (:name %)) (get dorian-hex-connections hex-name))])
    cps))



;; used-degrees
(let [used-degrees (-> dorian-hexanies-in-polydori-by-name
                       (select-keys (vals cps))
                       vals
                       (->> (mapcat :degrees))
                       set
                       sort)]
  {:size (count used-degrees)
   :used-degrees used-degrees})
