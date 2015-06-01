/**
 * The MIT License
 * Copyright (c) 2014 Agile Knowledge Engineering and Semantic Web (AKSW) (usbeck@informatik.uni-leipzig.de)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.aksw.gerbil.evaluate.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aksw.gerbil.evaluate.DoubleEvaluationResult;
import org.aksw.gerbil.evaluate.EvaluationResult;
import org.aksw.gerbil.evaluate.EvaluationResultContainer;
import org.aksw.gerbil.evaluate.Evaluator;
import org.aksw.gerbil.evaluate.SubTaskEvaluator;
import org.aksw.gerbil.transfer.nif.Marking;

import com.carrotsearch.hppc.DoubleArrayList;

public class SubTaskAverageCalculator<T extends Marking> implements Evaluator<T> {

    private List<SubTaskEvaluator<T>> evaluators;

    public SubTaskAverageCalculator(List<SubTaskEvaluator<T>> evaluators) {
        this.evaluators = evaluators;
    }

    @Override
    public void evaluate(List<List<T>> annotatorResults, List<List<T>> goldStandard, EvaluationResultContainer results) {
        EvaluationResultContainer subTaskResults = new EvaluationResultContainer();
        for (SubTaskEvaluator<T> evaluator : evaluators) {
            evaluator.evaluate(annotatorResults, goldStandard, subTaskResults);
        }
        addSubTaskResults(subTaskResults, results);
        addAverages(subTaskResults, results);
    }

    protected void addSubTaskResults(EvaluationResultContainer subTaskResults, EvaluationResultContainer results) {
        for (EvaluationResult result : subTaskResults.getResults()) {
            results.addResult(result);
        }
    }

    protected void addAverages(EvaluationResultContainer subTaskResults, EvaluationResultContainer results) {
        Map<String, DoubleArrayList> mapping = createNameValueMapping(subTaskResults.getResults());
        DoubleArrayList values;
        int subTaskCount = subTaskResults.getResults().size();
        double sum;
        for (String name : mapping.keySet()) {
            values = mapping.get(name);
            if (values.elementsCount == subTaskCount) {
                sum = 0;
                for (int i = 0; i < values.elementsCount; ++i) {
                    sum += values.buffer[i];
                }
                results.addResult(new DoubleEvaluationResult(name, sum / subTaskCount));
            }
        }
    }

    private Map<String, DoubleArrayList> createNameValueMapping(List<EvaluationResult> results) {
        Map<String, DoubleArrayList> mapping = new HashMap<String, DoubleArrayList>();
        for (EvaluationResult result : results) {
            addToMapping(mapping, result);
        }
        return mapping;
    }

    private void addToMapping(Map<String, DoubleArrayList> mapping, EvaluationResult result) {
        if (result instanceof EvaluationResultContainer) {
            for (EvaluationResult r : ((EvaluationResultContainer) result).getResults()) {
                addToMapping(mapping, r);
            }
        } else if (result instanceof DoubleEvaluationResult) {
            DoubleArrayList values;
            if (mapping.containsKey(result.getName())) {
                values = mapping.get(result.getName());
            } else {
                values = new DoubleArrayList();
                mapping.put(result.getName(), values);
            }
            values.add(((DoubleEvaluationResult) result).getValueAsDouble());
        }
    }

}
