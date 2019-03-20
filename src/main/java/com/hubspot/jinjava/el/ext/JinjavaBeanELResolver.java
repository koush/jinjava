package com.hubspot.jinjava.el.ext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;

/**
 * {@link BeanELResolver} supporting snake case property names.
 */
public class JinjavaBeanELResolver extends ELResolver {
  private interface MemoizeFunc<T> {
    T process() throws Exception;
  }

  private static class Memoize<T> {
    int hash(Object... objects) {
      int ret = 0;
      for (Object o: objects) {
        ret ^= o == null ? 0 : o.hashCode();
      }
      return ret;
    }

    HashMap<Integer, T> store = new HashMap<>();
    T memoize(MemoizeFunc<T> func, Object... args) throws Exception {
      int hash = hash(args);
      if (store.containsKey(hash)) {
        return store.get(hash);
      }
      T ret = func.process();
      store.put(hash, ret);
      return ret;
    }
  }

  boolean readOnly;

  /**
   * Creates a new read/write {@link JinjavaBeanELResolver}.
   */
  public JinjavaBeanELResolver() {}

  /**
   * Creates a new {@link JinjavaBeanELResolver} whose read-only status is determined by the given parameter.
   */
  public JinjavaBeanELResolver(boolean readOnly) {
    this.readOnly = readOnly;
  }

  @Override
  public Class<?> getCommonPropertyType(ELContext elContext, Object o) {
    return o == null ? null : Object.class;
  }

  @Override
  public Iterator getFeatureDescriptors(ELContext elContext, Object o) {
    return null;
  }

  @Override
  public Class<?> getType(ELContext context, Object base, Object property) {
    return null;
  }

  Memoize<Field> values = new Memoize<>();

  @Override
  public Object getValue(ELContext context, Object base, Object property) {
    try {
      Field found = values.memoize(() -> {
        if (Proxy.isProxyClass(base.getClass()))
          throw new IllegalArgumentException("unable to find field");
        for (Field field: base.getClass().getFields()) {
          if (field.getName().equals(property.toString())) {
            return field;
          }
        }
        throw new IllegalArgumentException("unable to find field");
      }, base.getClass(), property.toString());

      context.setPropertyResolved(true);
      return found.get(base);
    }
    catch (Exception e) {
      context.setPropertyResolved(false);
      return null;
    }
  }

  @Override
  public boolean isReadOnly(ELContext context, Object base, Object property) {
    return readOnly;
  }

  @Override
  public void setValue(ELContext context, Object base, Object property, Object value) {
    return;
  }

  Memoize<Method> invokes = new Memoize<>();

  @Override
  public Object invoke(ELContext context, final Object base, Object methodName, Class<?>[] paramTypes, Object[] params) {
    try {
      context.setPropertyResolved(true);

      ArrayList<Object> memo = new ArrayList<>();
      memo.add(base.getClass());
      memo.add(methodName);
      for (Object param: params) {
        if (param == null) {
          memo.add(null);
        }
        else {
          memo.add(param.getClass());
        }
      }
      Method found = invokes.memoize(() -> {
        for (Method method: base.getClass().getMethods()) {
          if (!method.getName().equals(methodName)) {
            continue;
          }
          Class[] paramTypes1 = method.getParameterTypes();
          if (paramTypes1.length != params.length) {
            continue;
          }

          for (int i = 0; i < params.length; i++) {
            if (!paramTypes1[i].isInstance(params[i])) {
              break;
            }
          }
          return method;
        }
        throw new IllegalArgumentException("unable to find method");
      }, memo.toArray());

      return found.invoke(base, params);
    }
    catch (Exception e) {
      context.setPropertyResolved(false);
      return null;
    }
  }
}
