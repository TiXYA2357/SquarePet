package xyz.lightsky.SquarePet.Prop;

import lombok.Getter;
import xyz.lightsky.SquarePet.Main;
import xyz.lightsky.SquarePet.Manager.ConfigManager;
import xyz.lightsky.SquarePet.Manager.MarketManager;
import xyz.lightsky.SquarePet.Prop.Symbol.PetAcceptable;
import xyz.lightsky.SquarePet.Prop.Symbol.TrainerAcceptable;
import xyz.lightsky.SquarePet.Trainer.Trainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public abstract class BaseProp {

    private static Map<Integer, BaseProp> propMap = new HashMap<>();

    public static void init() {
        registerProp(LuckyStrawProp.class);
        registerProp(LittleHPProp.class);
        registerProp(LittleSPProp.class);
        registerProp(MiddleHPProp.class);
        registerProp(MiddleSPProp.class);
        registerProp(LargeHPProp.class);
        registerProp(LargeSPProp.class);
        registerProp(HPEnhanceProp.class);
        registerProp(SPEnhanceProp.class);
        registerProp(SkillStoneProp.class);
        registerProp(ResurrectionStoneProp.class);
    }

    public static List<String> names = new ArrayList<>();

    @Deprecated
    public static void registerProp(int id, BaseProp prop) {
        if(id <= 10) return;
        propMap.put(id, prop);
        names.add(prop.getName());
    }

    public static void registerProp(Class<? extends BaseProp> clazz) {
        try {
            int id = clazz.getField("ID").getInt(null);
            if(!PetAcceptable.class.isAssignableFrom(clazz) && !TrainerAcceptable.class.isAssignableFrom(clazz)) {
                Main.warning(clazz.getSimpleName() + " register failed，please confirm you had implemented 'Acceptable'!");
                return;
            }
            BaseProp prop = clazz.newInstance();
            propMap.put(id, prop);
            names.add(prop.getName());
        } catch (NoSuchFieldException | IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
            Main.warning(clazz.getSimpleName() + " register failed!, do not find final field 'ID'");
        }
    }

    public static BaseProp getProp(int id) {
        return propMap.get(id);
    }

    @Override
    public String toString() {
        return getName();
    }

    public abstract String getName();

    public abstract int getId();

    public abstract String getInfo();

    public int get$Cost() {
        return MarketManager.getBaseProp$Cost(getId());
    }

    public boolean work(Trainer trainer) {
        return onUseToTrainer(trainer);
    }


    public boolean work(Trainer trainer, String petType) {
        return onUseToPet(trainer, petType);
    }

    boolean onUseToPet(Trainer trainer, String petType){
        return false;
    }

    boolean onUseToTrainer(Trainer trainer){
        return false;
    }
}
