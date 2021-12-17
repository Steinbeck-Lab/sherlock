/*
 * MIT License
 *
 * Copyright (c) 2020 Michael Wenk (https://github.com/michaelwenk)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openscience.sherlock.dbservice.result.service;

import org.openscience.sherlock.dbservice.result.model.db.ResultRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Service
public class ResultServiceImplementation
        implements ResultService {

    private final ResultRepository resultRepository;

    @Autowired
    public ResultServiceImplementation(final ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    @Override
    public Mono<Long> count() {
        return this.resultRepository.count();
    }

    @Override
    public Mono<ResultRecord> insert(final ResultRecord resultRecord) {
        return this.resultRepository.insert(resultRecord);
    }

    @Override
    public Mono<ResultRecord> findById(final String id) {
        return this.resultRepository.findById(id);
    }

    @Override
    public Flux<ResultRecord> findAll() {
        return this.resultRepository.findAll();
    }

    @Override
    public Mono<Void> deleteById(final String id) {
        return this.resultRepository.deleteById(id);
    }

    @Override
    public Mono<Void> deleteAll() {
        return this.resultRepository.deleteAll();
    }
}