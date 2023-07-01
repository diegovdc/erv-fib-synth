(ns tieminos.habitat.recording
  (:require
   [clojure.core.async :as a]
   [clojure.string :as str]
   [overtone.core :as o]
   [taoensso.timbre :as timbre]
   [tieminos.habitat.routing :refer [bus->bus-name input-number->bus*]]
   [tieminos.sc-utils.recording.v1 :as rec :refer [start-recording]]
   [tieminos.utils :refer [avg hz->ms normalize-amp]]))

(declare make-buf-key! add-analysis)

(defonce bufs (atom {}))

(defonce recording?
  ;;  {:input-name-kw boolean?}
  (atom {}))

(defn rec-input
  [{:keys [section subsection input-name input-bus dur-s on-end msg countdown]
    :or {on-end (fn [& args])
         msg "Recording"
         countdown 0}}]
  (let [input-kw (-> input-bus :name keyword)]
    (if (get @recording? input-kw)
      (timbre/warn "Input bus already recording: " input-kw)
      (do
        (swap! recording? assoc input-kw true)
        (start-recording
         :bufs-atom bufs
         :buf-key (make-buf-key! section subsection input-name)
         :input-bus input-bus
         :seconds dur-s
         :msg msg
         :on-end (fn [buf-key]
                   (swap! recording? assoc input-kw false)
                   (add-analysis dur-s buf-key input-bus)
                   (on-end buf-key))
         :countdown countdown)))))

(def habitat-samples-path (str (System/getProperty "user.dir")
                               "/samples/habitat_samples/"))

(defn save-samples [& {:keys [description full-keyword]}]
  (let [prefix  (or full-keyword
                    (keyword "habitat"
                             (str "*"
                                  (-> (.format (java.time.ZonedDateTime/now)
                                               java.time.format.DateTimeFormatter/ISO_INSTANT))
                                  (when description (str "*" (str/replace description #" " "-"))))))]
    (rec/save-samples prefix
                      :path habitat-samples-path
                      :buffers-atom bufs
                      :preserve-keys [:analysis :amp-norm-mult])))

(defonce buf-keys-count (atom {}))

(defn- make-buf-key! [section subsection input-name]
  (let [partial-name (format "%s-%s-%s" section subsection input-name)
        sample-number (get (swap! buf-keys-count update partial-name #(if % (inc %) 1))
                           partial-name)]
    (keyword (str partial-name "-" sample-number))))

(defn rec-controller
  [{:keys [in section subsection dur-s]
    :as osc-data}]
  (let [input-bus (input-number->bus* in)]
    (if (and input-bus section subsection dur-s)
      (rec-input {:section section
                  :subsection subsection
                  :input-name (bus->bus-name input-bus)
                  :input-bus input-bus
                  :dur-s dur-s})
      (throw (ex-info "Data missing from osc command for recording"
                      {:osc-data osc-data})))))

(comment
  (require '[tieminos.habitat.routing :refer [guitar-bus
                                              mic-1-bus
                                              mic-2-bus
                                              mic-3-bus]])
  (-> guitar-bus :name (str/replace #"-bus" ""))
  (rec-input {:section "test"
              :subsection "p1"
              :input-name "guitar"
              :input-bus guitar-bus
              :dur-s 5})

  (save-samples :description "test"))

;;;;;;;;;;;;;;;;;;;
;; Signal Analysis
;;;;;;;;;;;;;;;;;;;

(defonce analysis-history (atom {}))
(comment
  ;; TODO is freq being added to the analysis?
  (->> @analysis-history vals flatten (filter :freq?)))
(def analyzer-freq 60)

(defn run-get-signal-analysis
  [& {:keys [freq input-bus analysis-path]
      :or {freq 60}}]
  (timbre/debug "Initializing signal analyzer on bus:" input-bus)
  ((o/synth
    (let [input (o/in input-bus)]
      (o/send-reply (o/impulse freq) analysis-path
                    [(o/amplitude:kr input) (o/pitch:kr input)]
                    1)))))

(defn do-receive-analysis
  [input-bus-name-keyword data]
  (when (get @recording? input-bus-name-keyword)
    (let [[_node-id _input-bus amp freq freq?*] (-> data :args)
          freq? (= freq?* 1.0)]

      #_(timbre/debug input-bus-name-keyword "amp" amp "freq" freq)
      (swap! analysis-history
             update
             input-bus-name-keyword
             conj
             {:amp amp
              :timestamp (o/now)
              :freq freq
              :freq? freq?}))))

(defn run-receive-analysis
  "Gets analysis from `in` 0 in almost real time
  and `conj`es the data into`analysis-history.
  NOTE: `run-get-signal-pitches` must be running`"
  [& {:keys [freq input-bus-name-keyword analysis-path]}]
  (let [handler-name (keyword (str (str/replace analysis-path #"/" "")
                                   "-handler"))]
    (o/on-event analysis-path
                (fn [data] (do-receive-analysis input-bus-name-keyword data))
                handler-name)
    handler-name))

(comment
  (require '[tieminos.habitat.routing :refer [inputs]])
  (reset! recording? {})
  (-> @inputs :guitar :bus keys)
  (o/demo (o/in (-> @inputs :guitar :bus))))

(defn start-signal-analyzer
  [& {:keys [input-bus]}]
  (let [analysis-path (format "/receive-%s-analysis" (:name input-bus))]
    (timbre/debug "Starting analyzer with path" analysis-path)
    {:receiver (run-receive-analysis
                :freq analyzer-freq
                :input-bus-name-keyword (keyword (:name input-bus))
                :analysis-path analysis-path)
     :analyzer (run-get-signal-analysis
                :freq analyzer-freq
                :input-bus input-bus
                :analysis-path analysis-path)}))

(defn add-analysis [dur-s buf-key input-bus]
  (let [now (o/now)
        sample-start (- now (* 1000 dur-s)
                        ;; ensure samples window corresponds to dur-s
                        (hz->ms analyzer-freq))
        analysis (->> (get @analysis-history (keyword (:name input-bus)))
                      (drop-while #(> (:timestamp %) now))
                      (take-while #(>= (:timestamp %) sample-start))
                      (reduce (fn [acc {:keys [amp]}]
                                (-> acc
                                    (update :min-amp min amp)
                                    (update :max-amp max amp)
                                    (update :amps conj amp)))
                              {:min-amp 0
                               :max-amp 0
                               :amps ()}))
        buf (@bufs buf-key)
        avg-amp (avg (:amps analysis))
        amp-norm-mult (normalize-amp (:max-amp analysis))]
    (swap! bufs update buf-key
           assoc
           :rec/time sample-start
           :analysis (-> analysis
                         (assoc :avg-amp avg-amp)
                         (dissoc :amps))
           :amp-norm-mult amp-norm-mult)))

(defn norm-amp
  [buf]
  (:amp-norm-mult buf 1))

#_:clj-kondo/ignore
(comment
  (require '[tieminos.habitat.routing :refer [guitar-bus
                                              mic-1-bus
                                              mic-2-bus
                                              mic-3-bus
                                              mic-4-bus
                                              preouts]])
  (reset! analysis-history {})
  (o/stop)
  (->> @analysis-history
       :mic-1-bus
       (map :amp)
       sort
       reverse)
  (-> mic-1-bus)
  (swap! recording? assoc :mic-1-bus true) ;; manually turn on run-receive-analysis
  (swap! recording? assoc :mic-1-bus false)
  (def receiver-analyzer (start-signal-analyzer :input-bus mic-1-bus))
  (o/kill (:analyzer receiver-analyzer)))

(def test-samples (atom {}))

(comment
  (rec/load-own-samples!
   :buffers-atom test-samples
   :prefixes-set #{:habitat/test-samples-v1}
   :samples-path habitat-samples-path))

(comment
  #_:clj-kondo/ignore
  (require '[tieminos.utils :refer [rrange]])
  (let [buf-key :amanecer-subsection-guitar-1]
    (o/demo
     #_(o/sin-osc)
     (* (norm-amp (-> @bufs buf-key))
        (o/play-buf 1 (-> @bufs buf-key)))))
  (-> guitar-bus)
  (o/demo (o/in guitar-bus))
  (normalize-amp 0.5)
  (-> @bufs)
  (rec-input {:section "amanecer"
              :subsection "ide"
              :input-name "guitar"
              :input-bus guitar-bus
              :dur-s (rrange 7 15)})
  (rec-input {:section "amanecer"
              :subsection "ide"
              :input-name "mic-1"
              :input-bus mic-1-bus
              :dur-s (rrange 7 15)}))
