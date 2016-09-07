/*
 * Copyright (C) 2015 José Paumard
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package hu.akarnokd.comparison;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;


import ix.Ix;
import rx.functions.Func1;

/**
 *
 * @author José
 */
public class ShakespearePlaysScrabbleWithIxBeta extends ShakespearePlaysScrabble {

	/*
    Result: 12,690 ±(99.9%) 0,148 s/op [Average]
    		  Statistics: (min, avg, max) = (12,281, 12,690, 12,784), stdev = 0,138
    		  Confidence interval (99.9%): [12,543, 12,838]
    		  Samples, N = 15
    		        mean =     12,690 ±(99.9%) 0,148 s/op
    		         min =     12,281 s/op
    		  p( 0,0000) =     12,281 s/op
    		  p(50,0000) =     12,717 s/op
    		  p(90,0000) =     12,784 s/op
    		  p(95,0000) =     12,784 s/op
    		  p(99,0000) =     12,784 s/op
    		  p(99,9000) =     12,784 s/op
    		  p(99,9900) =     12,784 s/op
    		  p(99,9990) =     12,784 s/op
    		  p(99,9999) =     12,784 s/op
    		         max =     12,784 s/op


    		# Run complete. Total time: 00:06:26

    		Benchmark                                               Mode  Cnt   Score   Error  Units
    		ShakespearePlaysScrabbleWithRxJava.measureThroughput  sample   15  12,690 ± 0,148   s/op   
    		
    		Benchmark                                              Mode  Cnt       Score      Error  Units
			ShakespearePlaysScrabbleWithRxJava.measureThroughput   avgt   15  250074,776 ± 7736,734  us/op
			ShakespearePlaysScrabbleWithStreams.measureThroughput  avgt   15   29389,903 ± 1115,836  us/op
    		
    */ 
    @SuppressWarnings({ "unchecked", "unused" })
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(
		iterations=5
    )
    @Measurement(
    	iterations=5
    )
    @Fork(1)
    public List<Entry<Integer, List<String>>> measureThroughput() throws InterruptedException {

        // Function to compute the score of a given word
    	Func1<Integer, Ix<Integer>> scoreOfALetter = letter -> Ix.just(letterScores[letter - 'a']) ;
            
        // score of the same letters in a word
        Func1<Entry<Integer, LongWrapper>, Ix<Integer>> letterScore =
        		entry -> 
        			Ix.just(
    					letterScores[entry.getKey() - 'a']*
    					Integer.min(
    	                        (int)entry.getValue().get(), 
    	                        scrabbleAvailableLetters[entry.getKey() - 'a']
    	                    )
        	        ) ;
        
        Func1<String, Ix<Integer>> toIntegerIx = 
        		string -> Ix.from(IterableSpliterator.of(string.chars().boxed().spliterator())) ;
                    
        // Histogram of the letters in a given word
        Func1<String, Ix<HashMap<Integer, LongWrapper>>> histoOfLetters =
        		word -> toIntegerIx.call(word)
        					.collect(
    							() -> new HashMap<Integer, LongWrapper>(), 
    							(HashMap<Integer, LongWrapper> map, Integer value) -> 
    								{ 
    									LongWrapper newValue = map.get(value) ;
    									if (newValue == null) {
    										newValue = () -> 0L ;
    									}
    									map.put(value, newValue.incAndSet()) ;
    								}
    								
        					) ;
                
        // number of blanks for a given letter
        Func1<Entry<Integer, LongWrapper>, Ix<Long>> blank =
        		entry ->
        			Ix.just(
	        			Long.max(
	        				0L, 
	        				entry.getValue().get() - 
	        				scrabbleAvailableLetters[entry.getKey() - 'a']
	        			)
        			) ;

        // number of blanks for a given word
        Func1<String, Ix<Long>> nBlanks = 
        		word -> histoOfLetters.call(word)
        					.flatMap(map -> Ix.from(() -> map.entrySet().iterator()))
        					.flatMap(blank)
        					.sumLong();
        					
                
        // can a word be written with 2 blanks?
        Func1<String, Ix<Boolean>> checkBlanks = 
        		word -> nBlanks.call(word)
        					.flatMap(l -> Ix.just(l <= 2L)) ;
        
        // score taking blanks into account letterScore1
        Func1<String, Ix<Integer>> score2 = 
        		word -> histoOfLetters.call(word)
        					.flatMap(map -> Ix.from(() -> map.entrySet().iterator()))
        					.flatMap(letterScore)
        					.sumInt();
        					
        // Placing the word on the board
        // Building the streams of first and last letters
        Func1<String, Ix<Integer>> first3 = 
        		word -> Ix.from(IterableSpliterator.of(word.chars().boxed().limit(3).spliterator())) ;
        Func1<String, Ix<Integer>> last3 = 
        		word -> Ix.from(IterableSpliterator.of(word.chars().boxed().skip(3).spliterator())) ;
        		
        
        // Stream to be maxed
        Func1<String, Ix<Integer>> toBeMaxed = 
        	word -> Ix.fromArray(first3.call(word), last3.call(word))
        				.flatMap(observable -> observable) ;
            
        // Bonus for double letter
        Func1<String, Ix<Integer>> bonusForDoubleLetter = 
        	word -> toBeMaxed.call(word)
        				.flatMap(scoreOfALetter)
        				.maxInt();
            
        // score of the word put on the board
        Func1<String, Ix<Integer>> score3 = 
        	word ->
        		Ix.fromArray(
        				score2.call(word).map(v -> v * 2), 
        				bonusForDoubleLetter.call(word).map(v -> v * 2), 
        				Ix.just(word.length() == 7 ? 50 : 0)
        		)
        		.flatMap(observable -> observable)
        		.sumInt() ;

        Func1<Func1<String, Ix<Integer>>, Ix<TreeMap<Integer, List<String>>>> buildHistoOnScore =
        		score -> Ix.from(() -> shakespeareWords.iterator())
        						.filter(scrabbleWords::contains)
        						.filter(word -> checkBlanks.call(word).first())
        						.collect(
        							() -> new TreeMap<Integer, List<String>>(Comparator.reverseOrder()), 
        							(TreeMap<Integer, List<String>> map, String word) -> {
        								Integer key = score.call(word).first() ;
        								List<String> list = map.get(key) ;
        								if (list == null) {
        									list = new ArrayList<String>() ;
        									map.put(key, list) ;
        								}
        								list.add(word) ;
        							}
        						) ;
                
        // best key / value pairs
        List<Entry<Integer, List<String>>> finalList2 =
        		buildHistoOnScore.call(score3)
        			.flatMap(map -> Ix.from(() -> map.entrySet().iterator()))
        			.take(3)
        			.collect(
        				() -> new ArrayList<Entry<Integer, List<String>>>(), 
        				(list, entry) -> {
        					list.add(entry) ;
        				}
        			)
        			.first() ;
        			
        
//        System.out.println(finalList2);
        
        return finalList2 ;
    }
}