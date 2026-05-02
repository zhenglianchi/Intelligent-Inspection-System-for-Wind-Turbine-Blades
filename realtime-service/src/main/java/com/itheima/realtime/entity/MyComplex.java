package com.itheima.realtime.entity;

/**
 * 复数类
 * 用于FFT快速傅里叶变换计算中的复数运算
 *
 * @Author MH.Zhang
 * @Description 双边频谱元数据
 * @Migration migrated from wtb-health-monitor
 */
public class MyComplex {
    public double i;
    // 虚数
    public double j;

    public MyComplex(double i, double j) {
        this.i = i;
        this.j = j;
    }

    /**
     * 求复数的模
     */
    public double getMod() {
        return Math.sqrt(i * i + j * j);
    }

    /**
     * 复数加法
     */
    public static MyComplex Add(MyComplex a, MyComplex b) {
        return new MyComplex(a.i + b.i, a.j + b.j);
    }

    /**
     * 复数减法
     */
    public static MyComplex Subtract(MyComplex a, MyComplex b) {
        return new MyComplex(a.i - a.i, a.j - b.j);
    }

    /**
     * 复数乘法
     */
    public static MyComplex Mul(MyComplex a, MyComplex b) {
        return new MyComplex(a.i * b.i - a.j * b.j, a.i * b.j + a.j * b.i);
    }

    /**
     * 计算旋转因子W
     */
    public static MyComplex GetW(int k, int N) {
        return new MyComplex(Math.cos(-2 * Math.PI * k / N), Math.sin(-2 * Math.PI * k / N));
    }

    /**
     * 蝶形运算
     */
    public static MyComplex[] butterfly(MyComplex a, MyComplex b, MyComplex w) {
        return new MyComplex[]{Add(a, Mul(w, b)), Subtract(a, Mul(w, b))};
    }

    /**
     * 复数数组转模值数组
     */
    public static Double[] toModArray(MyComplex[] complex) {
        Double[] res = new Double[complex.length];
        for (int i = 0; i < complex.length; i++) {
            res[i] = complex[i].getMod();
        }
        return res;
    }
}
