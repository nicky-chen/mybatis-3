/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.binding;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

/**
 * Mapper jdk代理对象
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

    private static final long serialVersionUID = -6424540398559729838L;

    /**
     * SqlSession 对象
     */
    private final SqlSession sqlSession;
    private final Class<T> mapperInterface;
    private final Map<Method, MapperMethod> methodCache;

    public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
        this.sqlSession = sqlSession;
        this.mapperInterface = mapperInterface;
        this.methodCache = methodCache;
    }

    /**
     * 处理Mapper的核心方法，这里只是获取了mapper的方法名，然后通过SqlSession去处理，所以不需要mapper的实现类
     * @param proxy
     * @param method
     * @param args
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // <1> 如果是 Object 定义的方法，直接调用
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, args);
            } else if (isDefaultMethod(method)) {
                //判断是否为 default 修饰的方法
                //https://github.com/mybatis/mybatis-3/issues/709 ，支持 JDK8 default 方法
                return invokeDefaultMethod(proxy, method, args);
            }
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
        // <3.1> 获得 MapperMethod 对象
        final MapperMethod mapperMethod = cachedMapperMethod(method);
        // <3.2> 执行 MapperMethod 方法
        return mapperMethod.execute(sqlSession, args);
    }

    private MapperMethod cachedMapperMethod(Method method) {
        return methodCache
            .computeIfAbsent(method, k -> new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
    }

    private Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        final Constructor<MethodHandles.Lookup> constructor =
            MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        if (!constructor.isAccessible()) {
            constructor.setAccessible(true);
        }
        final Class<?> declaringClass = method.getDeclaringClass();
        return constructor.newInstance(declaringClass,
            MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED | MethodHandles.Lookup.PACKAGE
                | MethodHandles.Lookup.PUBLIC).unreflectSpecial(method, declaringClass).bindTo(proxy)
            .invokeWithArguments(args);
    }

    /**
     * Backport of java.lang.reflect.Method#isDefault()
     */
    private boolean isDefaultMethod(Method method) {
        return (method.getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC
            && method.getDeclaringClass().isInterface();
    }
}
