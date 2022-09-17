/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.flow.param;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.spi.Spi;

import java.util.List;

/**
 * A processor slot that is responsible for flow control by frequent ("hot spot") parameters.
 * 一个处理器插槽，负责通过频繁（“热点”）参数进行流量控制。
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 * @since 0.2.0
 */
@Spi(order = -3000)
public class ParamFlowSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args) throws Throwable {
        // 如果不存在热点参数流控规则 直接触发下一个节点
        if (!ParamFlowRuleManager.hasRules(resourceWrapper.getName())) {
            fireEntry(context, resourceWrapper, node, count, prioritized, args);
            return;
        }

        // 应用热点参数流控规则
        checkFlow(resourceWrapper, count, args);
        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        fireExit(context, resourceWrapper, count, args);
    }

    void applyRealParamIdx(/*@NonNull*/ ParamFlowRule rule, int length) {
        int paramIdx = rule.getParamIdx();
        if (paramIdx < 0) {
            if (-paramIdx <= length) {
                rule.setParamIdx(length + paramIdx);
            } else {
                // Illegal index, give it a illegal positive value, latter rule checking will pass.
                rule.setParamIdx(-paramIdx);
            }
        }
    }

    void checkFlow(ResourceWrapper resourceWrapper, int count, Object... args) throws BlockException {
        // 如果原始请求的参数为null 直接返回
        if (args == null) {
            return;
        }
        // 如果没有热点参数流控规则 直接返回
        if (!ParamFlowRuleManager.hasRules(resourceWrapper.getName())) {
            return;
        }
        // 从缓存map中获取所有的热点参数流控规则
        List<ParamFlowRule> rules = ParamFlowRuleManager.getRulesOfResource(resourceWrapper.getName());

        for (ParamFlowRule rule : rules) {
            // 设置参数的索引
            applyRealParamIdx(rule, args.length);

            // Initialize the parameter metrics. 初始化参数指标。
            ParameterMetricStorage.initParamMetricsFor(resourceWrapper, rule);

            if (!ParamFlowChecker.passCheck(resourceWrapper, rule, count, args)) {
                String triggeredParam = "";
                if (args.length > rule.getParamIdx()) {
                    Object value = args[rule.getParamIdx()];
                    // Assign actual value with the result of paramFlowKey method
                    if (value instanceof ParamFlowArgument) {
                        value = ((ParamFlowArgument) value).paramFlowKey();
                    }
                    triggeredParam = String.valueOf(value);
                }
                throw new ParamFlowException(resourceWrapper.getName(), triggeredParam, rule);
            }
        }
    }
}
