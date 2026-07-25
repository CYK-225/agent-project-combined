package org.example;/**
 * @Auter zzh
 * @Date 2025/10/11
 */


import org.example.common.proptcraft.compositePrompt.Skill;
import org.example.common.proptcraft.compositePrompt.ZeroShotPrompt;

/**
 * @projectName: AItest
 * @package: org.example.aitest.infrastructure.proptcraft.component
 * @className: testclient
 * @author: Eric
 * @description: TODO
 * @date: 2025/10/11 00:42
 * @version: 1.0
 */
//TODO
public class testclient {
    public static void main(String[] args) {

        ZeroShotPrompt prompt = new ZeroShotPrompt();

        ZeroShotPrompt test2=new ZeroShotPrompt();

        prompt.of("测试1").box( "问题","请简要介绍一下你自己？")
                .box("要求","回答要简洁明了，控制在50字以内。")
                .box("格式","使用第一人称叙述。");
        prompt.dag();
        test2.append(test2.of("测试2"));
        test2.append( test2.box( "问题","请简要介绍一下你自己？"));







        System.out.println(prompt.render());
        System.out.println("----------我是分割线-----------");
        System.out.println(test2.render());


        Skill.create("测试","这是一个测试技能")
                .skillSubSection("基本信息","ceshi")
                .box("输入","无").box("输出","无");
    }


}
