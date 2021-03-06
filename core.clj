(ns intseq2.core
  (:require [clojure.math.combinatorics :as com])
  (:require [clojure.math.numeric-tower :as maths])
  (:require [clojure.java.io :as io]))

;; Code for finding formulas for unknown integer sequences (Numbers 2).
;; By: Lee Jiaen, Scott Song, Conrad Kuklinsky, and Yushu Jiang

;; The top-level call function, gp, takes:
;; population-size = the number of individuals in the population
;; generations = the number of generations for which it will run evolution
;; test-pairs = list of the form ((input output)(input output) ...)
;; elitism = true or false for elitism
;; add-rate = add rate for UMAD mutation
;; delete-rate = delete rate for UMAD mutation
;; mutate? = true or false for mutation to occur with mutate
;; crossover? = true or false for crossover at one point to occurr
;; double_mutate? = true or false for mutation to occur with mutate or mutate2 (mutate2 1 every 20 times)
;; select-type = dictates if program does tournament or lexicase selection
;; base-mutate-rate = so that the program does not mutate every time even if mutate is true
;; double-rate = so that the program does not mutate every time even if double mutate is true
;; error-upper-limit = puts a limit so numbers do not get too high
;; tournament-size = size of each tournament for tournament selection

;; We will represent an individual as a map with these keys:
;; :genome = a vector of instructions and/or constants
;; :error = total error across test-pairs
;; :lexicase-error = error for lexicase selection

;; Programs are evaluated on a stack machine as follows:
;; - Constants are pushed on the stack
;; - Supported instructions pop needed values from the stack and push results on the stack
;; - If there aren't sufficient arguments for an instruction, then it does nothing

(def ingredients '(+ - * / x 0 1 log ! sqrt))

(defn factorial [n upper-limit]
  (if (< n 1)
    nil
    (loop [result 1
           iteration 1]
      ;;(println "iteration: " iteration ", result: " result)
      (if (> result upper-limit)
        nil
        (if (= iteration (+ n 1))
          (bigint result)
          (recur (* result iteration)(+ iteration 1)))))))

;;returns x^y
(defn pow [x y upper-limit]
  (if (< y 0)
    nil
    (loop [result 1
           iteration 0]
      (if (= y iteration)
        result
        (if (> result upper-limit)
          nil
          (recur (* result x)
                 (+ iteration 1)))))))


;;solves g^x = y for x
(defn discretelog [base target]
  (if (= target 1)
    0
    (if (or (<= base 1) (<= target 0))
      nil
      (let [exp (/ (Math/log target) (Math/log base))
            floor (Math/floor exp)
            ceiling (Math/ceil exp)]
        (if (= (pow base floor target) target )
          (bigint floor)
          (if (= (pow base ceiling target) target)
            (bigint ceiling)
            nil))))))

;;returns quotient only if the quotient is an integer value
(defn discretedivide [x divisor]
  (let [quotient (/ x divisor)
        floor (bigint (Math/floor quotient))
        ceiling (bigint (Math/ceil quotient))]
    (if (= (* floor divisor) x)
      floor
      (if (= (* ceiling divisor) x)
        ceiling
        nil))))

;;returns the square root only it the square root is an integer value
(defn discretesqrt [x]
  (if (< x 0)
    nil
    (let [sqrt (maths/sqrt x)
          floor (bigint (Math/floor sqrt))
          ceiling (bigint (Math/ceil sqrt))]
      (if (= (* floor floor) x)
        floor
        (if (= (* ceiling ceiling) x)
          ceiling
          nil)))))


;; In the following test the program multiplies x by 5.0. For the input 2.0 this will produce
;; 10.0, which is exactly what's specified, so the error for that will be 0. For the second
;; input, 3.0, this will produce 15.0, which will have an error of 1.0 since the specified
;; correct answer is 16.0. For the third input, -0.5, it will produce -2.5, which will have
;; an error of 0.5. So the total error should be 1.5.
#_(error '[5.0 x *]
         '((2.0 10.0) (3.0 16.0) (-0.5 -3.0)))


(defn error [genome test-pairs upper-limit]
  "Returns the error of genome in the context of test-pairs."
  (reduce + (for [pair test-pairs]
              (let [input (bigint (first pair))
                    output (bigint (second pair))]
                (loop [program genome
                       stack ()]
                  ;;(println "Program:" program "Stack:" stack)
                  (if (empty? program)
                    (if (or (empty? stack) (and (> (count stack) 0) (> (first stack) upper-limit)) (and (> (count stack) 1) (> (second stack) upper-limit)))
                      1000000.0
                      (Math/abs (double (- output (first stack))))) ;; Math/abs only takes in floating points, which causes the "No matching method abs found taking 1 args"
                    (recur (rest program)
                           (case (first program)
                             + (if (< (count stack) 2)
                                 stack
                                 (cons (+ (second stack) (first stack))
                                       (rest (rest stack))))
                             - (if (< (count stack) 2)
                                 stack
                                 (cons (- (second stack) (first stack))
                                       (rest (rest stack))))
                             * (if (< (count stack) 2)
                                 stack
                                 (cons (* (second stack) (first stack))
                                       (rest (rest stack))))
                             pow (if (< (count stack) 2)
                                   stack
                                   (let [result (pow (second stack) (first stack) 100)]
                                     (if (= result nil)
                                       stack
                                       (cons (pow (second stack) (first stack) 100)
                                             (rest (rest stack))))))
                             / (if (or (< (count stack) 2)
                                       (zero? (first stack)))
                                 stack
                                 (let [quotient (discretedivide (second stack) (first stack))]
                                   (if (= quotient nil)
                                     stack
                                     (cons quotient (rest (rest stack))))))
                             ! (if (< (count stack) 1)
                                 stack
                                 (let [result (factorial (first stack) upper-limit)]
                                   (if (= result nil)
                                     stack
                                     (cons result (rest stack)))))
                             log (if (< (count stack) 2)
                                   stack
                                   (let [exponent (discretelog (second stack) (first stack))]
                                     (if (= exponent nil)
                                       stack
                                       (cons exponent (rest(rest stack))))))
                             x (cons input stack)
                             mod (if (or (< (count stack) 2) (zero? (second stack)))
                                   stack
                                   (cons (long (mod (first stack) (second stack)))
                                         (rest (rest stack))))
                             sqrt (if (< (count stack) 1)
                                    stack
                                    (let [root (discretesqrt (first stack))]
                                      (if (= root nil)
                                        stack
                                        (cons root (rest stack)))))
                             gcd (if (< (count stack) 2)
                                   stack
                                   (cons (bigint (maths/gcd (second stack) (first stack)))
                                         (rest (rest stack))))
                             lcm (if (< (count stack) 2)
                                   stack
                                   (cons (bigint (maths/gcd (second stack) (first stack)))
                                         (rest (rest stack))))
                             per (if (< (count stack) 1)
                                   stack
                                   (cons (bigint (com/count-permutations (range (first stack))))
                                         (rest stack)))
                             comb (if (< (count stack) 2)
                                    stack
                                    (cons (bigint (com/count-combinations (range (first stack)) (second stack)))
                                          (rest stack)))
                             (cons (first program) stack)))))))))

(defn individual-error [genome input output error-upper-limit]
  "Returns the error of genome in the context of a single pair."
  (error genome (list (list input output)) error-upper-limit))

;; Made it so that the variable x has a higher chance of being in the
;  formula when the individual is first made because most
;  operations need at least two things on the stack
(defn new-formula [max-depth]
  "Creates a new formula of a certain depth."
  (vec (repeatedly max-depth #(if (< (rand) 0.67)
                                (first '(x)) (rand-nth ingredients)))))

(defn new-individual [test-pairs error-upper-limit]
  "Creates individual with genome, genome error, and lexicase-error keys"
  (let [form (new-formula 5)]
    {:genome         form
     :error          (error form test-pairs error-upper-limit)
     :lexicase-error 0}))

(defn best [individuals]
  "Returns the best (ties broken arbitrarily) of the given individuals."
  (reduce (fn [i1 i2]
            (if (< (:error i1) (:error i2))
              i1
              i2))
          individuals))

(defn tournament-select [population size]
  "Returns an individual selected from population using a tournament of a certain size"
  (best (repeatedly size #(rand-nth population))))

(defn set-lexicase-error [candidate case error-upper-limit]
  (let [input (first case)
        output (second case)]
    (assoc candidate :lexicase-error (individual-error (:genome candidate) input output error-upper-limit))))

(defn lexicase-select [population test-pairs error-upper-limit]
  "Returns an individual selected from population using lexicase selection."
  (loop [candidates (distinct population)
         cases (shuffle test-pairs)]
    (let [lexicase-candidates (map #(set-lexicase-error % (first cases) error-upper-limit) candidates) ;;need to add the test case error to each candidate
          min-error (apply min (map :lexicase-error lexicase-candidates))
          best-candidates (filter #(= min-error (:lexicase-error %)) lexicase-candidates)] ;;just apply min the smallest of the test case errors
      (if (or (= (count best-candidates) 1)
              (= (count cases) 1))
        (rand-nth best-candidates)                                 ;;in either case, pick a random candidate or it will return the only element in candidate
        (recur best-candidates (rest cases)) ;;filters out candidates with greater error than the min error
        ))))

(defn select [population type test-pairs size error-upper-limit]
  "Dictates what selection type the program does for the program based on the cases."
  (case type
    :tournament (tournament-select population size)
    :lexicase (lexicase-select population test-pairs error-upper-limit)
    )
  )

(defn mutate [genome add-rate delete-rate]
  "Returns a possibly-mutated copy of genome."
  (let [with-additions (flatten (for [g genome]
                                  (if (< (rand) add-rate)
                                    (shuffle (list g (rand-nth ingredients)))
                                    g)))
        with-deletions (flatten (for [g with-additions]
                                  (if (< (rand) delete-rate)
                                    ()
                                    g)))]
    (vec with-deletions)))

(defn mutate2 [genome]
  "Mutate 2 allows for a greater change/chance of mutation."
  (let [with-additions (flatten (for [g genome]
                                  (if (< (rand) 1/4)
                                    (shuffle (list g (rand-nth ingredients)))
                                    g)))
        with-deletions (flatten (for [g with-additions]
                                  (if (< (rand) 1/5)
                                    ()
                                    g)))]
    (vec with-deletions)))

(defn crossover [genome1 genome2]
  "Returns a random one-point crossover product of genome1 and genome2."
  (let [crossover-point (rand-int (inc (min (count genome1)
                                            (count genome2))))]
    (vec (concat (take crossover-point genome1)
                 (drop crossover-point genome2)))))

(defn make-child [population test-pairs add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit]
  "Returns a new, evaluated child, produced by a given mutation and selection combination."
  (let [genome1 (:genome (select population select-type test-pairs tournament-size error-upper-limit))
        genome2 (:genome (select population select-type test-pairs tournament-size error-upper-limit))
        crossover12 (crossover genome1 genome2)
        new-genome (if crossover? ;;if crossover
                     (if (and mutate? (< (rand) base-mutate-rate))  ;;if crossover, determine if we should mutate
                       (if double_mutate? ;;if double mutation
                         (if (< (rand) double-rate) ;; if crossover and double mutation (no mutation)
                           (mutate2 crossover12) ;;new genome is crossover+mutated if < 1/2
                           (mutate crossover12 add-rate delete-rate)) ;;new genome is crossover+mutated2 if > 1/2
                         (mutate crossover12 add-rate delete-rate)) ;;new genome is just crossover and normal mutate
                       crossover12)
                     (if (and mutate? (< (rand) base-mutate-rate)) ;; if no crossover, determine if we should mutate
                       (if double_mutate? ;; mutation will happen, determine if we will double mutate
                         (if (< (rand) double-rate) ;; double mutation here based on chance
                           (if (< (rand) 1/2)
                             (mutate2 genome1) ;; mutate2 genome1 if < 1/20 and then < 1/2
                             (mutate2 genome2)) ;; mutate2 genome2 if < 1/20 and the > 1/2
                           (if (< (rand) 1/2)
                             (mutate genome1 add-rate delete-rate) ;; mutate genome1 if > 1/20 and then < 1/2
                             (mutate genome2 add-rate delete-rate))) ;; mutate genome2 if > /120 and then > 1/2
                         (if (< (rand) 1/2) ;; no double mutation, use normal mutate
                           (mutate genome1 add-rate delete-rate) ;; mutate genome1 if > 1/20 and then < 1/2
                           (mutate genome2 add-rate delete-rate))) ;; mutate genome2 if > /120 and then > 1/2
                       (if (< (rand) 1/2) ;;mutation will not happen
                         genome1 ;;if no crossover, no mutate, no double mutate return genome1 as new genome if < 1/2
                         genome2));;if no crossover, no mutate, no double mutate return genome2 as new genome if > 1/2
                     )]
    {:genome         new-genome
     :error          (error new-genome test-pairs error-upper-limit)
     :lexicase-error 0}))

(defn report [generation population]
  "Prints a report on the status of the population at the given generation."
  (let [current-best (best population)]
    (println {:generation   generation
              :best-error   (:error current-best)
              :diversity    (float (/ (count (distinct population))
                                      (count population)))
              :population-size (count population)
              :average-size (float (/ (->> population
                                           (map :genome)
                                           (map count)
                                           (reduce +))
                                      (count population)))
              :best-genome  (:genome current-best)})))

(defn gp-main [population-size generations test-pairs name elitism add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit export-output]
  "Runs genetic programming to solve, or approximately solve, a
  sequence problem in the context of the given population-size,
  number of generations to run, test-pairs, specified mutation and
  selection combination, addition and deletion rates, and an upper limit error. This version
  of gp has mutate, double mutate, crossover, and exporting to a txt file as conditionals."
  (loop [population (repeatedly population-size
                                #(new-individual test-pairs error-upper-limit))
         generation 0]
    (report generation population)
    (if (or (< (:error (best population)) 0.1)
            (>= generation generations))
      (if export-output
        (let [filename (str name "_" population-size "_" generations "_" elitism
                            "_" (float add-rate) "_" (float delete-rate) "_" mutate? "_" crossover? "_" double_mutate? "_" select-type
                            "_" (float base-mutate-rate) "_" (float double-rate) "_" tournament-size "_" error-upper-limit ".txt")
              filename2 (str name "_" population-size "_" generations "_" elitism
                             "_" (float add-rate)  ".txt")]
          (if (.exists (io/file filename))
            (spit filename (str :generation " " generation " " (best population) "\n") :append true)
            (spit filename (str :generation " " generation " " (best population) "\n" )))))
      (if elitism
        (recur (conj (repeatedly (dec population-size)
                                 #(make-child population test-pairs add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit))
                     (best population))
               (inc generation))
        (recur (repeatedly population-size
                           #(make-child population test-pairs add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit))
               (inc generation))))))

(defn -main [& args]
  ;; population-size generations test-pairs name elitism add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit export-output
  "Runs genetic programming to solve, or approximately solve, a
  sequence problem in the context of the given population-size,
  number of generations to run, test-pairs, specified mutation and
  selection combination, addition and deletion rates, and an upper limit error. This version
  of gp has mutate, double mutate, crossover, and exporting to a txt file as conditionals.
  THIS IS USED FOR RUNNING IN COMMAND LINE FOR CLUSTER"
  (let [population-size (read-string (nth args 0))
        generations (read-string (nth args 1))
        test-pairs (read-string (nth args 2))
        name (read-string (nth args 3))
        elitism (read-string (nth args 4))
        add-rate (read-string (nth args 5))
        delete-rate (read-string (nth args 6))
        mutate? (read-string (nth args 7))
        crossover? (read-string (nth args 8))
        double_mutate? (read-string (nth args 9))
        select-type (read-string (nth args 10))
        base-mutate-rate (read-string (nth args 11))
        double-rate (read-string (nth args 12))
        tournament-size (read-string (nth args 13))
        error-upper-limit (read-string (nth args 14))
        export-output (read-string (nth args 15))]
    (loop [population (repeatedly population-size
                                  #(new-individual test-pairs error-upper-limit))
           generation 0]
      (report generation population)
      (if (or (< (:error (best population)) 0.1)
              (>= generation generations))
        (if export-output
          (let [filename (str name "_" population-size "_" generations "_" elitism
                              "_" (float add-rate) "_" (float delete-rate) "_" mutate? "_" crossover? "_" double_mutate? "_" select-type
                              "_" (float base-mutate-rate) "_" (float double-rate) "_" tournament-size "_" error-upper-limit ".txt")
                filename2 (str name "_" population-size "_" generations "_" elitism
                               "_" (float add-rate)  ".txt")]
            (if (.exists (io/file filename))
              (spit filename (str :generation " " generation " " (best population) "\n") :append true)
              (spit filename (str :generation " " generation " " (best population) "\n" )))))
        (if elitism
          (recur (conj (repeatedly (dec population-size)
                                   #(make-child population test-pairs add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit))
                       (best population))
                 (inc generation))
          (recur (repeatedly population-size
                             #(make-child population test-pairs add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit))
                 (inc generation)))))))

(def testseq
  (let [seq [1, 1, 2, 5, 11, 26, 68, 177, 497, 1476, 4613, 15216, 52944, 193367, 740226, 2960520]
        ind (range (count seq))]
    (map #(vec [%1 %2]) ind seq)))

;; A simple test, symbolic regression of x^2 + x + 1
(def simple-regression-data
  (for [x (range -100 100 1)]
    [x (+ (* x x) x 1)]))

;; x^3 - x + 1
(def polynomial
  (for [x (range -100 100 1)]
    [x (+ 1 (+ x (- (pow x 3 1000000) (* x 2))))]))

;; x^3 - x^2 + x+ 1
(def polynomial2
  (for [x (range -100 100 1)]
    [x (+ 1 (+ x (- (pow x 3 1000000) (pow x 2 1000000))))]))

;; x^5 + x^2 + 6
(def polynomial3 
  (for [x (range -20 20 1)]  
                            [x (+ 6 (+ (* x x) (pow x 5)))]))

;;These are set to have population of 200, max 100 gen, crossover, mutation with a 1/10 addition rate
;;and 1/11 deletion rate, tournament/lexicase selection, 8/10 base mutation rate for mutate and double mutate.
;;tournament size, and to have a txt output
#_(gp-main 200 100 polynomial "polynomial" true 1/10 1/11 true true false :tournament 8/10 8/10 10 10000000 true)
#_(gp-main 200 100 polynomial "polynomial" true 1/10 1/11 true true false :lexicase 8/10 8/10 10 10000000 true)

#_(gp-main 200 100 polynomial2 "polynomial2" true 1/10 1/11 true true false :tournament 8/10 8/10 10 10000000 true)
#_(gp-main 200 100 polynomial2 "polynomial2" true 1/10 1/11 true true false :lexicase 8/10 8/10 10 10000000 true)

#_(gp-main 200 100 polynomial3 "polynomial3" true 1/10 1/11 true true false :tournament 8/10 8/10 10 10000000 true)
#_(gp-main 200 100 polynomial3 "polynomial3" true 1/10 1/11 true true false :lexicase 8/10 8/10 10 10000000 true)


#_(gp-main 200 100 testseq "testseq" true 1/10 1/11 true true false :tournament 8/10 8/10 10 10000000 true)
#_(gp-main 200 100 testseq "testseq" true 1/10 1/11 true true false :lexicase 8/10 8/10 10 10000000 true)

(defn gp_error [population-size generations test-pairs elitism add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit]
  (loop [population (repeatedly population-size
                                #(new-individual test-pairs error-upper-limit))
         generation 0]
    (if (or (< (:error (best population)) 0.1)
            (>= generation generations))
      (best population)
      (if elitism
        (recur (conj (repeatedly (dec population-size)
                                 #(make-child population test-pairs add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit))
                     (best population))
               (inc generation))
        (recur (repeatedly population-size
                           #(make-child population test-pairs add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit))
               (inc generation))))))

(defn average_error [population-size generations test-pairs name elitism add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit export-output]
  (loop [sum 0 run 0]
    (if (>= run 100)
      (if export-output
        (let [filename (str name "_" population-size "_" generations "_" elitism
                            "_" (float add-rate) "_" (float delete-rate) "_" mutate? "_" crossover? "_" double_mutate? "_" select-type
                            "_" (float base-mutate-rate) "_" (float double-rate) "_" tournament-size "_" error-upper-limit ".txt")]
          (if (.exists (io/file filename))
            (spit filename (str "average error is:" " " (/ sum 100) "\n") :append true)
            (spit filename (str "average error is:" " " (/ sum 100) "\n")))))
      (recur (+ (:error(gp_error population-size generations test-pairs elitism add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit)) sum) (inc run)))))

#_(average_error 200 100 testseq "testseq" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)
#_(average_error 200 100 simple-regression-data "simple-regression-data" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)
#_(average_error 200 100 polynomial "polynomial" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)
#_(average_error 200 100 polynomial2 "polynomial2" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)
#_(average_error 200 100 polynomial3  "polynomial3" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)

(defn gp_gen [population-size generations test-pairs elitism add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit]
  (loop [population (repeatedly population-size
                                #(new-individual test-pairs error-upper-limit))
         generation 0]
    (if (or (< (:error (best population)) 0.1)
            (>= generation generations))
      generation
      (if elitism
        (recur (conj (repeatedly (dec population-size)
                                 #(make-child population test-pairs add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit))
                     (best population))
               (inc generation))
        (recur (repeatedly population-size
                           #(make-child population test-pairs add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit))
               (inc generation))))))

(defn average_gen [population-size generations test-pairs name elitism add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit export-output]
  (loop [sum 0 run 0]
    (if (>= run 100)
      (if export-output
        (let [filename (str name "_" population-size "_" generations "_" elitism
                            "_" (float add-rate) "_" (float delete-rate) "_" mutate? "_" crossover? "_" double_mutate? "_" select-type
                            "_" (float base-mutate-rate) "_" (float double-rate) "_" tournament-size "_" error-upper-limit ".txt")]
          (if (.exists (io/file filename))
            (spit filename (str "average generation is:" " " (/ sum 100) "\n") :append true)
            (spit filename (str "average generation is:" " " (/ sum 100) "\n")))))
      (recur (+ (gp_gen population-size generations test-pairs elitism add-rate delete-rate mutate? crossover? double_mutate? select-type base-mutate-rate double-rate tournament-size error-upper-limit) sum) (inc run)))))

#_(average_gen 200 100 testseq "testseq" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)
#_(average_gen 200 100 simple-regression-data "simple-regression-data" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)
#_(average_gen 200 100 polynomial "polynomial" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)
#_(average_gen 200 100 polynomial2 "polynomial2" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)
#_(average_gen 200 100 polynomial3  "polynomial3" true 1/10 1/11 true true false :tournament 4/5 4/5 10 true)
