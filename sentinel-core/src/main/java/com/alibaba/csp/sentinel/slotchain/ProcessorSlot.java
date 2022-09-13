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
package com.alibaba.csp.sentinel.slotchain;

import com.alibaba.csp.sentinel.context.Context;

/**
 * A container of some process and ways of notification when the process is finished.
 *
 * @author qinan.qn
 * @author jialiang.linjl
 * @author leyou(lihao)
 * @author Eric Zhao
 */
public interface ProcessorSlot<T> {

    /**
     * Entrance of this slot.
     * 此插槽的入口。
     *
     * @param context         current {@link Context}      上下文
     * @param resourceWrapper current resource             包装后的资源对象
     * @param param           generics parameter, usually is a {@link com.alibaba.csp.sentinel.node.Node}  泛型参数，通常是一个 {@link com.alibaba.csp.sentinel.node.Node}
     * @param count           tokens needed                     一般是1
     * @param prioritized     whether the entry is prioritized  默认是false
     * @param args            parameters of the original call   原始调用的参数
     * @throws Throwable blocked exception or unexpected error
     */
    void entry(Context context, ResourceWrapper resourceWrapper, T param, int count, boolean prioritized,
               Object... args) throws Throwable;

    /**
     * Means finish of {@link #entry(Context, ResourceWrapper, Object, int, boolean, Object...)}.
     * 表示 {@link #entry(Context, ResourceWrapper, Object, int, boolean, Object...)} 的完成。转换下一个节点执行
     *
     * @param context         current {@link Context}
     * @param resourceWrapper current resource
     * @param obj             relevant object (e.g. Node)
     * @param count           tokens needed
     * @param prioritized     whether the entry is prioritized
     * @param args            parameters of the original call
     * @throws Throwable blocked exception or unexpected error
     */
    void fireEntry(Context context, ResourceWrapper resourceWrapper, Object obj, int count, boolean prioritized,
                   Object... args) throws Throwable;

    /**
     * Exit of this slot.
     * 此插槽的退出。
     *
     * @param context         current {@link Context}
     * @param resourceWrapper current resource
     * @param count           tokens needed
     * @param args            parameters of the original call
     */
    void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args);

    /**
     * Means finish of {@link #exit(Context, ResourceWrapper, int, Object...)}.
     *
     * @param context         current {@link Context}
     * @param resourceWrapper current resource
     * @param count           tokens needed
     * @param args            parameters of the original call
     */
    void fireExit(Context context, ResourceWrapper resourceWrapper, int count, Object... args);
}
