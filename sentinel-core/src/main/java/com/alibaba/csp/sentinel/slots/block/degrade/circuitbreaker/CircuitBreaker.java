/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;

/**
 * <p>Basic <a href="https://martinfowler.com/bliki/CircuitBreaker.html">circuit breaker</a> interface.</p>
 * <p>
 * 从sentinel1.8中，将三熔断策略（慢调用、异常比例、异常数）封装为两种熔断器
 * ExceptionCircuitBreaker          异常熔断器
 * ResponseTimeCircuitBreaker       响应时间熔断器
 *
 * @author Eric Zhao
 */
public interface CircuitBreaker {

    /**
     * Get the associated circuit breaking rule.
     *
     * @return associated circuit breaking rule
     */
    DegradeRule getRule();

    /**
     * Acquires permission of an invocation only if it is available at the time of invoking.
     * 仅当调用时可用时才获得调用许可。
     *
     * @param context context of current invocation
     * @return {@code true} if permission was acquired and {@code false} otherwise
     */
    boolean tryPass(Context context);

    /**
     * Get current state of the circuit breaker.
     * 获取断路器的当前状态。
     *
     * @return current state of the circuit breaker
     */
    State currentState();

    /**
     * <p>Record a completed request with the context and handle state transformation of the circuit breaker.</p>
     * 使用上下文记录完成的请求并处理断路器的状态转换。
     * <p>Called when a <strong>passed</strong> invocation finished.</p>
     * 当 passed（请求通过）调用完成时调用。
     *
     * @param context context of current invocation
     */
    void onRequestComplete(Context context);

    /**
     * Circuit breaker state.
     */
    enum State {
        /**
         * In {@code OPEN} state, all requests will be rejected until the next recovery time point.
         * 在 {@code OPEN} 状态下，所有请求都将被拒绝，直到下一个恢复时间点。
         */
        OPEN,
        /**
         * In {@code HALF_OPEN} state, the circuit breaker will allow a "probe" invocation.
         * If the invocation is abnormal according to the strategy (e.g. it's slow), the circuit breaker
         * will re-transform to the {@code OPEN} state and wait for the next recovery time point;
         * otherwise the resource will be regarded as "recovered" and the circuit breaker
         * will cease cutting off requests and transform to {@code CLOSED} state.、
         * 在 {@code HALF_OPEN} 状态下，断路器将允许“探测”调用（尝试性调用）。
         * 如果按策略调用异常（比如很慢），断路器将重新转换为{@code OPEN}状态，等待下一个恢复时间点；
         * 否则资源将被视为“已恢复”，断路器将停止切断请求并转换为 {@code CLOSED} 状态。
         */
        HALF_OPEN,
        /**
         * In {@code CLOSED} state, all requests are permitted. When current metric value exceeds the threshold,
         * the circuit breaker will transform to {@code OPEN} state.
         * 在 {@code CLOSED} 状态下，所有请求都被允许。当当前度量值超过阈值时，断路器将转换为 {@code OPEN} 状态。
         */
        CLOSED
    }
}
