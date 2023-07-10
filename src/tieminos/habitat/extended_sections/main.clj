(ns tieminos.habitat.extended-sections.main
  "For setting up different recompositions of Habitat.
  i.e. like playing only different sections, and extending them."
  (:require
   [erv.cps.core :as cps]
   [erv.scale.core :as scale]
   [taoensso.timbre :as timbre]
   [tieminos.habitat.init :refer [habitat-initialized? init!]]
   [tieminos.habitat.main :as main]
   [tieminos.habitat.main-sequencer :as hseq :refer [subsequencer]]
   [tieminos.habitat.parts.amanecer :as amanecer]
   [tieminos.habitat.parts.noche :as noche]
   [tieminos.habitat.recording  :as rec :refer [norm-amp recording?]]
   [tieminos.habitat.routing :refer [inputs preouts]]
   [tieminos.habitat.scratch.sample-rec2 :refer [hacia-un-nuevo-universo-perc-refrain
                                                 hacia-un-nuevo-universo-perc-refrain-v1p2
                                                 rising-upwards
                                                 start-rec-loop!
                                                 start-rec-loop2!]]
   [tieminos.habitat.utils :refer [open-inputs-with-rand-pan]]
   [tieminos.utils :refer [rrange]]
   [time-time.dynacan.players.gen-poly :as gp]))

(comment
  main/context
  main/performance-config
  main/start-sequencer!
  main/stop-sequencer!)

(def intercambios-de-energia
  {:context
   (assoc main/context
          :intercambios-de-energia/min-width 2
          :intercambios-de-energia/input-pairs-fn (fn [{:keys [inputs texto-sonoro-rand-mixer-bus]}]
                                                    (let [ts {:bus @texto-sonoro-rand-mixer-bus}]
                                                      [[(:guitar @inputs) ts]
                                                       [(:guitar @inputs) (:mic-1 @inputs)]
                                                       #_[(:guitar @inputs) (:mic-3 @inputs)]
                                                       [ts (:mic-1 @inputs)]
                                                       #_[(:mic-2 @inputs) ts]
                                                       #_[(:mic-3 @inputs) ts]
                                                       #_[(:mic-4 @inputs) ts]]))
          :intercambios-de-energia/convolver-in2-amp 0.3
          :intercambios-de-energia/convolver-max-amp-fn #(rrange 0.2 0.5))
   :sections [[[0 0] (fn [_])]
              [[0 2] #'amanecer/intercambios-de-energia]
              [[10 25] (fn [_] (println "end"))]] #_(main/quick-sections 5 sections)})

(comment
  (main/start-sequencer! intercambios-de-energia))

(defn polinizadores-nocturnos*
  [context]
  (noche/fuego context)
  (Thread/sleep 200)
  (noche/fuego-stop context)
  (noche/polinizadores-nocturnos context)

  (subsequencer
   :sequencer/polinizadores-nocturnos
   context
   (let [scale-1 (->> (cps/make 3 [9 13 15 21 27 31]) :scale)
         make-sample-player-config (fn [scale]
                                     {:buf-fn (fn [_] (-> @rec/bufs vals rand-nth))
                                      :period-dur 20
                                      :total-durs 20
                                      :loop? true
                                      :refrain-id :rising-upwards-loop
                                      :synth-params (fn [{:keys [buf i]}]
                                                      {:amp (* (rrange 0.2 1) (norm-amp buf))
                                                       :rate (scale/deg->freq scale 1 (+ (mod i 43)))})})]
     [[[52 22] (fn [_] (start-rec-loop!))]
      [[53 0] (fn [_] (rising-upwards (make-sample-player-config scale-1)))]
      [[55 0] (fn [_] (rising-upwards (-> (make-sample-player-config scale-1)
                                          (assoc :period-dur 4))))]
      [[55 10] (fn [_]
                 (gp/stop :rising-upwards-loop)
                 (reset! rec/bufs {}))]
      [[57 0] (fn [_] (rising-upwards (make-sample-player-config scale-1)))]
      [[59 0] (fn [_] (reset! rec/bufs {}))]
      [[60 0] (fn [_] (rising-upwards (-> (make-sample-player-config scale-1)
                                          (assoc :period-dur 4))))]
      [[60 10] (fn [_] (rising-upwards (make-sample-player-config scale-1)))]
      [[61 0] (fn [_]
                (reset! rec/bufs {})
                (rising-upwards (-> (make-sample-player-config scale-1)
                                    (assoc :period-dur 4))))]
      [[62 10] (fn [_]
                 (gp/stop :rec-loop)
                 (gp/stop :rising-upwards-loop))]])))

(def polinizadores-nocturnos
  ;; TODO revisar refrains de emision hay cosas raras (aumentos de volumen y saturación del servidor)
  {:context (merge main/context {})
   :sections [[[52 22] #'polinizadores-nocturnos*]
              [[62 10] (fn [_] (println "end"))]]
   :initial-sections #'polinizadores-nocturnos*
   ;; :rec? true
   })

(defn hacia-un-nuevo-universo*
  [context]
  (noche/hacia-un-nuevo-universo context)
  (subsequencer
   :sequencer/hacia-un-nuevo-universo
   context

    ;; TODO coso percusivo con samples ganulados, breves y reson-eco-reverb, tal vez convlucieon
    ;; Siempre usar últimos sonidos tocados
    ;; Transponer siempre espectral

   [[[62 10]
     (fn [_context]
       (start-rec-loop2!
        {:input-bus-fn (fn [_] (-> @inputs (select-keys [:guitar :mic-1]) vals rand-nth :bus))
         :durs (mapv (fn [_] (rrange 5 10)) (range 40))})
       (hacia-un-nuevo-universo-perc-refrain
        {:buf-fn (fn [_] (->> @rec/bufs vals (sort-by :rec/time) reverse (filter :analysis) (take 5) (#(when (seq %) (rand-nth %)))))
         :rates (map #(* 2 %) [1 6 7 11 9 2 12 8 5 13]) #_(range 1 10)
         :amp 1
         :period 10
         :durs [2 3 5 3]
         :d-weights {8 1
                     5 1
                     3 1}
         :d-level-weights {0.3 5
                           0.1 2
                           0.2 3
                           0.4 2}
         :a-weights {(rrange 0.01 0.3) 2
                     (rrange 1 2) 3
                     (rrange 2 5) 1}}))]

    [[67 0] (fn [_]
              (gp/stop :rec-loop2)
              (gp/stop :hacia-un-nuevo-universo-perc2))]]))

(def hacia-un-nuevo-universo
  {:context (merge main/context {})
   :sections [[[62 10] #'hacia-un-nuevo-universo*]
              #_[[67 45] #'noche/hacia-un-nuevo-universo-stop]]
   :initial-sections #'hacia-un-nuevo-universo*
   :rec? true})

(comment
  ;; impro sobre hacia un nuevo universo
  (def fib-21 [{:ratio 1, :bounded-ratio 1024/987, :bounding-period 2} {:ratio 4181/4096, :bounded-ratio 4181/3948, :bounding-period 2} {:ratio 17/16, :bounded-ratio 1088/987, :bounding-period 2} {:ratio 17711/16384, :bounded-ratio 17711/15792, :bounding-period 2} {:ratio 9/8, :bounded-ratio 384/329, :bounding-period 2} {:ratio 305/256, :bounded-ratio 1220/987, :bounding-period 2} {:ratio 5/4, :bounded-ratio 1280/987, :bounding-period 2} {:ratio 323/256, :bounded-ratio 1292/987, :bounding-period 2} {:ratio 21/16, :bounded-ratio 64/47, :bounding-period 2} {:ratio 5473/4096, :bounded-ratio 5473/3948, :bounding-period 2} {:ratio 89/64, :bounded-ratio 1424/987, :bounding-period 2} {:ratio 1449/1024, :bounded-ratio 69/47, :bounding-period 2} {:ratio 377/256, :bounded-ratio 1508/987, :bounding-period 2} {:ratio 3/2, :bounded-ratio 512/329, :bounding-period 2} {:ratio 1597/1024, :bounded-ratio 1597/987, :bounding-period 2} {:ratio 13/8, :bounded-ratio 1664/987, :bounding-period 2} {:ratio 6765/4096, :bounded-ratio 2255/1316, :bounding-period 2} {:ratio 55/32, :bounded-ratio 1760/987, :bounding-period 2} {:ratio 28657/16384, :bounded-ratio 28657/15792, :bounding-period 2} {:ratio 233/128, :bounded-ratio 1864/987, :bounding-period 2} {:ratio 987/512, :bounded-ratio 2N, :bounding-period 2}])
  (defn fib-chord [degs] (->> degs
                              (map (fn [deg]
                                     (scale/deg->freq fib-21 1 deg)))))
  (defn fib-chord-seq [chords]
    (map fib-chord chords))

  (defn transpose-chord [chord transpositions]
    (map (fn [t] (map (fn [deg] (+ t deg)) chord)) transpositions))
  (transpose-chord [0 1 2] [0 1 2 3])
  (fib-chord-seq (transpose-chord [0 1 2] [0 1 2 3]))

  (start-rec-loop2!
    {:input-bus-fn (fn [_] (-> @inputs (select-keys [:guitar :mic-1 :mic-2]) vals rand-nth :bus))
     :durs (mapv (fn [_] (rrange 5 10)) (range 40))})

  (open-inputs-with-rand-pan
    {:inputs inputs
     :preouts preouts})

  (gp/stop :rec-loop2)
  (map #(* 2 %) [1 6 7 11 9 2 12 8 5 13])
  (fib-chord-seq (transpose-chord [0 5 17] [20 24 19 27 33]))

  (hacia-un-nuevo-universo-perc-refrain-v1p2
    {:buf-fn (fn [_] (->> @rec/bufs vals (sort-by :rec/time) reverse (filter :analysis) (take 5) (#(when (seq %) (rand-nth %)))))
     :rates #_(interleave (fib-chord-seq (transpose-chord [0 9 16] [20 19 2 27 23 34 50 48])
                                         #_(transpose-chord [0 5 13 21] (map #(- % 21) [20 28 25 31 39 27])))
                          (fib-chord-seq (transpose-chord [8] [20 19 2 27 23 34 50 48]))
                          (fib-chord-seq (transpose-chord [-2 13 18] [20 19 27 23 3 34 50 48]))
                          (fib-chord-seq (transpose-chord [3] [20 19 2 27 23 34 50 48]))
                          (fib-chord-seq (transpose-chord [-15 21] [20 19 27 23 34 50 48 0]))
                          (fib-chord-seq (transpose-chord [13] [20 19 2 27 23 34 50 48])))
     #_(concat (fib-chord-seq (transpose-chord [0 5 13 21] (map #(- % 21) [20 28 25 31 39 27])))
               (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 2 (- % 21)) [20 28 25 31 39 27])))
               (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 4 (- % 21)) [20 28 25 31 39 27]))))
     (interleave (fib-chord-seq (transpose-chord [0 5 13 21] (range 0 (* 21 6) 5)))
                 (reverse (fib-chord-seq (transpose-chord [0 5 13 21] (range 0 (* 21 6) 5)))))
     :amp 0.6
     :period 30
     :durs [2 3 5 3 8]
     :d-weights {8 1
                 5 1
                 3 1}
     :d-level-weights {0.3 5
                       0.1 2
                       0.2 3
                       0.4 2}
     :a-weights {(rrange 0.01 0.2) 1/4
                 (rrange 0.2 0.8) 1
                 (rrange 1 2) 3
                 (rrange 2 5) 1}})


  ;; TODO renombrar
  (defn hacia-un-nuevo-universo-live
    [context]
    (subsequencer
      :sequencer/hacia-un-nuevo-universo-live
      context

      [[[87 00] (fn [_]
                  (println "S1-=============")
                  (open-inputs-with-rand-pan
                    {:inputs inputs
                     :preouts preouts}))]
       [[90 00]
        (fn [_]
          (start-rec-loop2!
            {:input-bus-fn (fn [_] (-> @inputs (select-keys [:guitar :mic-1 :mic-2]) vals rand-nth :bus))
             :durs (mapv (fn [_] (rrange 5 10)) (range 40))})
          (hacia-un-nuevo-universo-perc-refrain-v1p2
            {:buf-fn (fn [_] (->> @rec/bufs vals (sort-by :rec/time) reverse (filter :analysis) (take 5) (#(when (seq %) (rand-nth %)))))
             :rates #_(interleave (fib-chord-seq (transpose-chord [0 9 16] [20 19 2 27 23 34 50 48])
                                                 #_(transpose-chord [0 5 13 21] (map #(- % 21) [20 28 25 31 39 27])))
                                  (fib-chord-seq (transpose-chord [8] [20 19 2 27 23 34 50 48]))
                                  (fib-chord-seq (transpose-chord [-2 13 18] [20 19 27 23 3 34 50 48]))
                                  (fib-chord-seq (transpose-chord [3] [20 19 2 27 23 34 50 48]))
                                  (fib-chord-seq (transpose-chord [-15 21] [20 19 27 23 34 50 48 0]))
                                  (fib-chord-seq (transpose-chord [13] [20 19 2 27 23 34 50 48])))
             #_(concat (fib-chord-seq (transpose-chord [0 5 13 21] (map #(- % 21) [20 28 25 31 39 27])))
                       (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 2 (- % 21)) [20 28 25 31 39 27])))
                       (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 4 (- % 21)) [20 28 25 31 39 27]))))
             (interleave (fib-chord-seq (transpose-chord [0 5 13 21] (range 0 (* 21 6) 5)))
                         (reverse (fib-chord-seq (transpose-chord [0 5 13 21] (range 0 (* 21 6) 5)))))
             :amp 0.6
             :period 30
             :durs [2 3 5 3 8]
             :d-weights {8 1
                         5 1
                         3 1}
             :d-level-weights {0.3 5
                               0.1 2
                               0.2 3
                               0.4 2}
             :a-weights {(rrange 0.01 0.2) 1/4
                         (rrange 0.2 0.8) 1
                         (rrange 1 2) 3
                         (rrange 2 5) 1}}))]
       [[95 00]
        (fn [_]
          (hacia-un-nuevo-universo-perc-refrain-v1p2
            {:buf-fn (fn [_] (->> @rec/bufs vals (sort-by :rec/time) reverse (filter :analysis) (take 5) (#(when (seq %) (rand-nth %)))))
             :rates #_(interleave (fib-chord-seq (transpose-chord [0 9 16] [20 19 2 27 23 34 50 48])
                                                 #_(transpose-chord [0 5 13 21] (map #(- % 21) [20 28 25 31 39 27])))
                                  (fib-chord-seq (transpose-chord [8] [20 19 2 27 23 34 50 48]))
                                  (fib-chord-seq (transpose-chord [-2 13 18] [20 19 27 23 3 34 50 48]))
                                  (fib-chord-seq (transpose-chord [3] [20 19 2 27 23 34 50 48]))
                                  (fib-chord-seq (transpose-chord [-15 21] [20 19 27 23 34 50 48 0]))
                                  (fib-chord-seq (transpose-chord [13] [20 19 2 27 23 34 50 48])))
             #_(concat (fib-chord-seq (transpose-chord [0 5 13 21] (map #(- % 21) [20 28 25 31 39 27])))
                       (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 2 (- % 21)) [20 28 25 31 39 27])))
                       (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 4 (- % 21)) [20 28 25 31 39 27]))))
             (interleave (fib-chord-seq (transpose-chord [0 5 13 21] (range (* 21 -3) (* 21 6) 5)))
                         (reverse (fib-chord-seq (transpose-chord [0 5 13 21] (range (* 21 -3) (* 21 6) 5)))))
             :amp 0.4
             :period 20
             :durs [1 2 3]
             :d-weights {10 1
                         15 1
                         13 1}
             :d-level-weights {0.3 5
                               0.1 2
                               0.2 3
                               0.4 2}
             :a-weights {(rrange 8 15) 4
                         (rrange 2 5) 1}}))]
       [[100 00]
        (fn [_]
          (hacia-un-nuevo-universo-perc-refrain-v1p2
            {:buf-fn (fn [_] (->> @rec/bufs vals (sort-by :rec/time) reverse (filter :analysis) (take 5) (#(when (seq %) (rand-nth %)))))
             :rates #_(interleave (fib-chord-seq (transpose-chord [0 9 16] [20 19 2 27 23 34 50 48])
                                                 #_(transpose-chord [0 5 13 21] (map #(- % 21) [20 28 25 31 39 27])))
                                  (fib-chord-seq (transpose-chord [8] [20 19 2 27 23 34 50 48]))
                                  (fib-chord-seq (transpose-chord [-2 13 18] [20 19 27 23 3 34 50 48]))
                                  (fib-chord-seq (transpose-chord [3] [20 19 2 27 23 34 50 48]))
                                  (fib-chord-seq (transpose-chord [-15 21] [20 19 27 23 34 50 48 0]))
                                  (fib-chord-seq (transpose-chord [13] [20 19 2 27 23 34 50 48])))
             #_(concat (fib-chord-seq (transpose-chord [0 5 13 21] (map #(- % 21) [20 28 25 31 39 27])))
                       (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 2 (- % 21)) [20 28 25 31 39 27])))
                       (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 4 (- % 21)) [20 28 25 31 39 27]))))
             (interleave (fib-chord-seq (transpose-chord [0 5 13 21] (range (* 21 -3) (* 21 6) 5)))
                         (reverse (fib-chord-seq (transpose-chord [0 5 13 21] (range (* 21 -3) (* 21 6) 5)))))
             :amp 0.6
             :period 30
             :durs [2 3 5 3 8]
             :d-weights {8 1
                         5 1
                         3 1}
             :d-level-weights {0.3 5
                               0.1 2
                               0.2 3
                               0.4 2}
             :a-weights {(rrange 0.01 0.2) 1/4
                         (rrange 0.2 0.8) 1
                         (rrange 1 2) 3
                         (rrange 2 5) 1}}))]
       [[100 00]
        (fn [_]
          (hacia-un-nuevo-universo-perc-refrain-v1p2
            {:buf-fn (fn [_] (->> @rec/bufs vals (sort-by :rec/time) reverse (filter :analysis) (take 5) (#(when (seq %) (rand-nth %)))))
             :rates (interleave (fib-chord-seq (transpose-chord [0 9 16] [20 19 2 27 23 34 50 48]))
                                (fib-chord-seq (transpose-chord [8] [20 19 2 27 23 34 50 48]))
                                (fib-chord-seq (transpose-chord [-2 13 18] [20 19 27 23 3 34 50 48]))
                                (fib-chord-seq (transpose-chord [3] [20 19 2 27 23 34 50 48]))
                                (fib-chord-seq (transpose-chord [-15 21] [20 19 27 23 34 50 48 0]))
                                (fib-chord-seq (transpose-chord [13] [20 19 2 27 23 34 50 48])))
             #_(concat (fib-chord-seq (transpose-chord [0 5 13 21] (map #(- % 21) [20 28 25 31 39 27])))
                       (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 2 (- % 21)) [20 28 25 31 39 27])))
                       (fib-chord-seq (transpose-chord [0 5 13 21] (map #(* 4 (- % 21)) [20 28 25 31 39 27]))))

             :amp 0.6
             :period 15
             :d-weights {8 1
                         5 1
                         3 1}
             :d-level-weights {0.3 5
                               0.1 2
                               0.2 3
                               0.4 2}
             :a-weights {(rrange 0.01 0.2) 1/4
                         (rrange 0.2 0.8) 1
                         (rrange 1 2) 3
                         (rrange 2 5) 1}}))]

       [[105 0] (fn [_]
                 (gp/stop :rec-loop2)
                  (gp/stop :hacia-un-nuevo-universo-perc2))]]))
  (def hacia-un-nuevo-universo-impro
    {:context (merge main/context {})
     :sections [[[87 00] #'hacia-un-nuevo-universo-live]]
     :initial-sections #'hacia-un-nuevo-universo-live
     :rec? true
     })
  :rfc)




(comment
  (reset! recording? {})
  (reset! rec/bufs {})
  (main/start-sequencer! hacia-un-nuevo-universo-impro))

(comment
  ;; TODO generar función para abrir y cerrar los micros para probar
  (-> @hseq/context)
  (fn [_] (-> @inputs (select-keys [:guitar :mic-1 :mic-2]) vals rand-nth :bus))
  (timbre/set-level! :info)
  (do  (when @habitat-initialized?
         (main/stop-sequencer! hseq/context))
       (init!)))
