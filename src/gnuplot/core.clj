(ns gnuplot.core
  (:refer-clojure :exclude [format list range])
  (:require [clojure.core :as c]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [byte-streams :as bs]))

(defprotocol Command
  "Protocol for formatting things as Gnuplot commands."
  (format [x] "Format this thing as a Gnuplot command string."))

(extend-protocol Command
  clojure.lang.Symbol
  (format [x] (name x))

  clojure.lang.Keyword
  (format [x] (name x))

  String
  (format [x] (str "'" (str/replace x #"'" "\\'") "'"))

  clojure.lang.Seqable
  (format [x] (str/join " " (map format x)))

  Object
  (format [x] (str x)))

(defrecord Literal [^String s]
  Command
  (format [l] s))

(defn lit
  "A literal string, formatted exactly as it is."
  [s]
  (Literal. s))

(defrecord Range [lower upper]
  Command
  (format [r] (str "[" (format lower) ":" (format upper) "]")))

(defn range
  "A gnuplot range, formatted as [lower:upper]"
  [lower upper]
  (Range. lower upper))

(defrecord List [xs]
  Command
  (format [l] (str/join "," (map format xs))))

(defn list
  "A gnuplot comma-separated list, rendered as a,b,c"
  [& elements]
  (List. elements))

(defn run!
  "Opens a new gnuplot process, runs the given command string, and feeds it the
  given input stream, waits for the process to exit, and returns a map of its
  `{:exit code, :out string, and :err string}`.

  Asserts that gnuplot exits with 0; if not, throws an ex-info like
  `{:type :gnuplot, :exit 123, :out ..., :err ...}`."
  [commands input]
  (println (bs/convert input String))
  (let [results (sh "gnuplot"
                    "-p"
                    "-e"      commands
                    :in       input
                    :out-enc  "UTF-8")]
    (if (zero? (:exit results))
      results
      (throw (ex-info (str "Gnuplot error: " (:err results))
                      (assoc results :type :gnuplot))))))

(def dataset-separator "\ne\n")

(defn plot!
  "Takes a sequence of Commands, and invokes gnuplot with them."
  [commands datasets]
  (run! (->> commands
             (map format)
             (str/join ";\n"))
        (->> datasets
             (map (fn ds-format [dataset]
                    (concat
                      (->> dataset
                           (map (fn point-format [point]
                                  (str/join " " point)))
                           (interpose "\n"))
                      (list dataset-separator))))
             bs/to-input-stream)))