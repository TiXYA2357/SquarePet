package xyz.lightsky.squarepet.pet;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.entity.data.FloatEntityData;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.DestroyBlockParticle;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.StringTag;
import cn.nukkit.scheduler.Task;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import xyz.lightsky.squarepet.form.Pet;
import xyz.lightsky.squarepet.language.Lang;
import xyz.lightsky.squarepet.manager.ConfigManager;
import xyz.lightsky.squarepet.manager.PetManager;
import xyz.lightsky.squarepet.manager.TrainerManager;
import xyz.lightsky.squarepet.pet.animation.AnimationController;
import xyz.lightsky.squarepet.pet.pathfinder.astar.AstarPathfinder;
import xyz.lightsky.squarepet.pet.pathfinder.utils.Util;
import xyz.lightsky.squarepet.skill.BaseSkill;
import xyz.lightsky.squarepet.utils.Tools;
import xyz.lightsky.squarepet.trainer.Trainer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Setter
@Getter
@ToString(exclude = {"owner", "seatPosition", "dataTag", "minScale", "maxScale"})
public class BaseSquarePet extends EntityHuman {

    private Trainer owner;
    private String ownerName;
    private String name;
    private BaseSkill[] skills = new BaseSkill[3];
    private List<String> skillNames = new ArrayList<>();
    private int sp;
    private int lv;
    private int exp;
    private int maxSP;
    private int maxExp;
    private double defenceRate;
    private int cd;
    private String modelName;
    private String type;
    private Attribute attribute;
    private double attack;
    private double attackSpeed;
    private double critRate;
    private double critTimeRate;
    private double minScale;
    private double maxScale;
    private int spRecoverRate;
    private double spLossRate;
    private CompoundTag dataTag;
    private Vector3f seatPosition;

    private boolean isInLineup;

    private List<String> foodRaw = new ArrayList<>();
    private int maxLv;

    private List<Item> foodItems = new ArrayList<>();

    private boolean canSeat;

    private boolean canSkill = true;

    private Entity target = null;

    private int attackDelay;

    private boolean autoSkill;

    /**濒死状态*/
    private boolean preDead = false;

    private PetResourceCache ache;

    /** Skill effect add */
    private int attackAdd;
    private double defenceRateAdd;
    private double critRateAdd;
    private double critTimeRateAdd;


    private Vector2f randomPosition = new Vector2f();


    public BaseSquarePet(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);

        /* after super(), skillTags, skillNames foodRaw, foodItems will be initialized */
        ListTag<StringTag> skillTags = dataTag.getList("技能", StringTag.class);
        for(int i=0;i<skillTags.size();i++) {
            addSkill(BaseSkill.get(skillTags.get(i).data));
        }

        ListTag<StringTag> foods = dataTag.getList("食物", StringTag.class);
        for (int i = 0; i < foods.size(); i ++) {
            addFood(foods.get(i).data);
        }
        ache = owner.getPetMap().get(getType());
        autoSkill = ache.isAutoSkill();

        float randomX = new Random().nextFloat() + 2F;
        float randomZ = new Random().nextFloat() + 2F;
        if((new Random()).nextBoolean()) {
            randomPosition.add(randomX);
            if((new Random()).nextBoolean()) {
                randomPosition.add(0, randomZ);
            }else {
                randomPosition.add(0, -randomZ);
            }
        }else {
            randomPosition.add(-randomX);
            if((new Random()).nextBoolean()) {
                randomPosition.add(0, randomZ);
            }else {
                randomPosition.add(0, -randomZ);
            }
        }
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        // todo: use PetResourceCache to transfer data
        CompoundTag nbt = namedTag;
        dataTag = nbt.getCompound("SquareDATA");
        ownerName = dataTag.getString("主人");
        owner = TrainerManager.getTrainer(ownerName);
        name = dataTag.getString("名称");
        sp = dataTag.getInt("SP");
        maxSP = dataTag.getInt("最大SP");
        type = dataTag.getString("类型");
        attribute = Attribute.of(dataTag.getString("属性"));
        lv = dataTag.getInt("等级");
        exp = dataTag.getInt("经验");
        setMaxHealth(dataTag.getInt("最大血量"));
        setHealth(dataTag.getInt("血量"));
        attack = dataTag.getDouble("攻击");
        attackSpeed = dataTag.getDouble("攻速");
        attackDelay = (int) (attackSpeed * 20);
        defenceRate = dataTag.getDouble("防御");
        critRate = dataTag.getDouble("暴击率");
        critTimeRate = dataTag.getDouble("暴击倍率");
        cd = dataTag.getInt("CD") * 20;
        setScale((float) dataTag.getDouble("大小"));
        spRecoverRate = dataTag.getInt("SP恢复速率");
        spLossRate = dataTag.getDouble("SP损耗率");

        minScale = PetManager.getMinSize(type);
        maxScale = PetManager.getMaxSize(type);
        modelName = dataTag.getString("模型");
        seatPosition = PetManager.getBaseMountedOffSet(type).multiply(getScale());
        canSeat = PetManager.canSeat(type);
        maxExp = PetManager.getPetNeedExp(type, lv);
        maxLv = PetManager.getMaxLv(type);
        setNameTagAlwaysVisible();
        setNameTagAlwaysVisible(true);
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if(preDead) {
            source.setCancelled(true);
        }
        if(source instanceof EntityDamageByEntityEvent) {
            if(((EntityDamageByEntityEvent) source).getDamager().equals(getOwner().getPlayer())){
                Player owner = (Player) ((EntityDamageByEntityEvent) source).getDamager();
                if(owner.isSneaking()){
                    Pet.PET_SNAPSHOT(this);
                }
                source.setCancelled(true);
                return false;
            }
            double finalDefenceRate = defenceRate + defenceRateAdd;
            if(isInLineup) {
                finalDefenceRate += getOwner().getLineup().getDefenceRateAdd();
            }
            double denfencedRate = 1D - finalDefenceRate;
            source.setDamage(Math.max((float) (source.getDamage() * denfencedRate), 1F));
        }
        return super.attack(source);
    }

    public void onDamage(Entity target) {
        if(target.equals(getOwner().getPlayer())) return;
        double finalCritRate = getLuckilyUpRate() + critRate + critRateAdd;
        double finalCritTimeRate = 1D;
        double finalAttack = attack + attackAdd;
        if(isInLineup) {
            finalCritRate += getOwner().getLineup().getCritRateAdd();
            finalAttack += getOwner().getLineup().getAttackAdd();
        }
        int rate = (int) (finalCritRate * 100);
        boolean crit = false;
        if(new Random().nextInt(100) <= rate) {
            finalCritTimeRate = critTimeRate + critTimeRateAdd;
            if(isInLineup) {
                finalCritTimeRate += getOwner().getLineup().getCritTimeRateAdd();
            }
            crit = true;
        }
        DestroyBlockParticle particle = new DestroyBlockParticle(target.add(0, getEyeHeight()), Block.get(Block.REDSTONE_BLOCK));
        getLevel().addParticle(particle);
        finalAttack *= finalCritTimeRate;
        float finalRandom = new Random().nextFloat();
        finalAttack = new Random().nextBoolean() ? (finalAttack - finalRandom) : (finalAttack + finalRandom);
        if(ConfigManager.showAttack()) {
            Tools.createDamageParticle(target, (float) finalAttack, crit);
        }
        target.attack((float) finalAttack);
    }

    @Override
    public boolean mountEntity(Entity entity) {
        if(isCanSeat()) {
            getOwner().setOnRide(this);
            return super.mountEntity(entity);
        }
        return false;
    }

    @Override
    public boolean dismountEntity(Entity entity) {
        getOwner().setOnRide(null);
        return super.dismountEntity(entity);
    }

    public boolean addSkill(BaseSkill skill) {
        if(skill == null) return false;
        if(skill.getAttribute().equals(Attribute.ALL) || skill.getAttribute().equals(getAttribute())) {
            if(skills[0] == null) {
                skills[0] = skill;
                skillNames.add(skill.getName());
            }else if(skills[1] == null) {
                skills[1] = skill;
                skillNames.add(skill.getName());
            }else if(skills[2] == null) {
                skills[2] = skill;
                skillNames.add(skill.getName());
            }else {
                getOwner().sendMessage(Lang.translate("%user.pet.addskill.full%"));
                return false;
            }
        }else {
            getOwner().sendMessage(Lang.translate("%user.pet.addskill.cannot.learn%"));
            return false;
        }
        return true;
    }

    public void addFood(String foodRaw) {
        if(foodRaw == null) return;
        int id = Integer.parseInt(foodRaw.split(":")[0]);
        int meta = Integer.parseInt(foodRaw.split(":")[1]);
        Item food = Item.get(id, meta);
        foodItems.add(food);
        if(!this.foodRaw.contains(foodRaw)) {
            this.foodRaw.add(foodRaw);
        }
    }


    /** Do not allow to save NBT*/
    @Override
    public void saveNBT() {
    }

    public void save() {
        getOwner().refreshAche(this);
        getAche().save();
    }

    @Override
    public void close() {
        save();
        super.close();
    }

    @Override
    public void kill() {
        preDead = true;
        updatePreDead();
        getOwner().sendMessage(Lang.translate("%user.pet.dead%").replace("{Name}", getName()));
        super.kill();
        getOwner().closePet(getType());
    }

    public boolean feed(Item food) {
        ArrayList<Integer> ids = (ArrayList<Integer>) getFoodItems().stream().map(Item::getId).collect(Collectors.toList());
        ArrayList<Integer> metas = (ArrayList<Integer>) getFoodItems().stream().map(Item::getDamage).collect(Collectors.toList());
        if(!ids.contains(food.getId()) || !metas.contains(food.getDamage())) {
            getOwner().sendMessage(Lang.translate("%user.pet.donot.like.eat%").replace("{Name}", getName()));
            return false;
        }
        boolean bool = new Random().nextBoolean();
        if(bool) {
            int amount = new Random().nextInt(6);
            healHP(amount);
            healSP(1);
            getOwner().sendMessage(Lang.translate("%user.pet.eat.recover").replace("{Name}", getName()).replace("{Amount}", String.valueOf(amount)));
        }
        updateNameTag();
        return true;
    }

    public void removeSkill(int index) {
        skills[index] = null;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        //updatePreDead();
        if(preDead) {
            target = null;
            return super.onUpdate(currentTick);
        }
        cd --;
        attackDelay --;
        if(cd <= 0) {
            cd = dataTag.getInt("CD") * 20;
            canSkill = true;
        }
        if(attackDelay <= 0) {
            attackDelay = (int) (attackSpeed * 20);
            if(target != null && this.distanceSquared(target) <= Math.pow(PetManager.getAttackRange(getType()), 2)) {
                onDamage(target);
            }
        }
        /*  Reduce the number of refreshes */
        if(currentTick % 5 == 0) {
            checkLineup();
            updateNameTag();
            checkTarget();
            checkOnGround();
            checkInWater();
            checkOwnerDistance();
            updateScale();
            if(target != null && autoSkill) {
                autoSkill();
            }
            if(target != null) {
                moveTo(target);
            }else {
                moveTo(getOwner().getPlayer().getPosition().add(randomPosition.x, 0, randomPosition.y));
            }
        }

        if(currentTick % 10 == 0) {
            if(target != null){
                look(target);
            }else {
                look(getOwner().getPlayer());
            }
        }

        return super.onUpdate(currentTick);
    }

    public void checkOwnerDistance() {
        if(Util.MHDistance(this, getOwner().getPlayer()) >= 100) {
            this.teleport(getOwner().getPlayer());
        }
    }

    public void checkTarget() {
        if(this.target != null) {
            if (!this.target.isAlive()) {
                // TODO: 2021/07/16 经验自适应
                gainExp(new Random().nextInt(20));
                this.target = null;
                return;
            }
            if(this.distanceSquared(target) >= Math.pow(PetManager.getHatredRange(getType()), 2)) {
                this.target = null;
            }
        }
    }

    //direct motion to target
    //target is locked! or fixSpeed is more than 5!
    //Hope that fixSpeed ​​is a factor of 10
    public void moveToTarget(float fixSpeed) {
        if(target == null) return;
        int[] j = new int[]{0};
        int divide = (int) Util.MHDistance(this, target);
        double dx = (target.x - this.x) / divide;
        double dy = (target.y - this.y) / divide;
        double dz = (target.z - this.z) / divide;
        Server.getInstance().getScheduler().scheduleRepeatingTask(new Task() {
            @Override
            public void onRun(int i) {
                j[0] ++;
                move(dx, dy, dz);
                updateMovement();
                if(j[0] >= divide) {
                    getHandler().cancel();
                }
            }
        }, Math.max((int) (10F / fixSpeed), 1));
    }

    public void moveTo(Position pos) {
        if(getPassenger() != null) return;
        // wait for 1 tick = 50 ms
        // need to complete in 250 ms = 5 tick
        Vector3 next = new AstarPathfinder(this, pos).find();
        Vector3 start = this.getPosition();
        if(next != null) {
            AtomicInteger ai = new AtomicInteger();
            // 5 - 1 = 4 tick
            Server.getInstance().getScheduler().scheduleRepeatingTask(new Task() {
                @Override
                public void onRun(int i) {
                    ai.incrementAndGet();
                    double dx = (next.x - start.x) * 0.25;
                    double dy = (next.y - start.y) * 0.25;
                    double dz = (next.z - start.z) * 0.25;
                    move(dx, dy, dz);
                    updateMovement();
                    if(ai.get() >= 4) {
                        getHandler().cancel();
                    }
                }}, 2);
        }
    }

    public void look(Entity pos) {
        if(getPassenger() != null) return;
        double horizontal = Math.sqrt(Math.pow((pos.x - this.x),2) + Math.pow((pos.z - this.z),2));
        double vertical = pos.y + pos.getEyeHeight() - this.y - 0.2;
        double atan2 = - Math.atan2(vertical, horizontal) / Math.PI * 180;

        if(atan2 > 90) {
            this.pitch = atan2 - 180;
        }else if(atan2 > -90) {
            this.pitch = atan2;
        }else if(atan2 > -180) {
            this.pitch = atan2 + 180;
        }

        double xDist = pos.x - this.x;
        double zDist = pos.z - this.z;
        this.yaw = Math.atan2(zDist, xDist) / Math.PI * 180 - 90;
        if(this.yaw < 0){
            this.yaw += 360.0F;
        }
    }

    public void checkOnGround() {
        if(getAttribute().equals(Attribute.LAND)) {
            // path finder has completed this task for no passenger, but sometimes path finder will not find path as pet float on the sky
            if(Util.isPermeable(getLevel().getBlock(this.add(0, -0.5, 0)))){
                move(0, -0.5, 0);
            }
        }
        if(getAttribute().equals(Attribute.SWIM)) {
            if(isInLineup && !isInsideOfWater()) {
                /* float on the sky */
                if(Util.isPermeable(getLevel().getBlock(this.add(0, -3, 0)))){
                    move(0, -0.8, 0);
                }
            }else {
                if(this.getLevelBlock() instanceof BlockAir) {
                    move(0, -0.5, 0);
                }
            }
        }
    }

    public Vector3f checkJump(Vector2f motion) {
        float dx = motion.x;
        float dz = motion.y;
        float dy = 0F;
        if(!Util.isPermeable(getLevel().getBlock(this.add(dx + 0.5, 0, dz + 0.5)))) {
            dy = 1F;
        }
        return new Vector3f(dx, dy , dz);
    }

    public void checkInWater() {
        boolean checked = isInsideOfWater();
        if(!getAttribute().equals(Attribute.SWIM) && checked) {
            getOwner().sendMessage(Lang.translate("%user.pet.cannot.touch.water%").replace("{Name}", getName()));
            getOwner().closePet(getType());
        }
        if(getAttribute().equals(Attribute.SWIM) && !checked && !isInLineup) {
            getOwner().sendMessage(Lang.translate("%user.pet.cannot.leave.water%").replace("{Name}", getName()));
            getOwner().closePet(getType());
        }
    }

    public void checkLineup() {
        boolean checked = getOwner().getLineup().contains(getType());
        isInLineup = checked;
        getOwner().getPetMap().get(getType()).setInLineup(checked);
    }

    public void updateMaxExp() {
        maxExp = PetManager.getPetNeedExp(type, lv);
        getOwner().getPetMap().get(getType()).setMaxExp(maxExp);
    }

    public void updatePreDead() {
        getOwner().getPetMap().get(getType()).setPreDead(preDead);
    }


    public void levelUP() {
        levelJump(1);
    }

    public void levelJump(int value) {
        if(value == 0) return;
        if(this.lv != maxLv) {
            int lv = Math.min(this.lv + value, maxLv);
            getOwner().sendMessage(Lang.translate("%user.pet.level.jump%").replace("{Name}", getName()).replace("{lv}", String.valueOf(lv)));
        }
        this.lv = Math.min(this.lv + value, maxLv);
        updateMaxExp();
        setMaxSP(Math.max(PetManager.getPetCurrentMaxSP(getType(), lv), getMaxSP()));
        setMaxHealth(Math.max(PetManager.getPetCurrentMaxHP(getType(), lv), getMaxHealth()));
        setAttack(Math.max(PetManager.getPetCurrentAttack(getType(), lv), getAttack()));
        getOwner().refreshAche(this);
    }

    private void gainExp(int value, int count) {
        this.exp += value;
        if(this.exp >= maxExp) {
            this.exp -= maxExp;
            count ++;
            maxExp = PetManager.getPetNeedExp(type, this.lv + count);
            gainExp(0, count);
        }else {
            levelJump(count);
        }
    }

    public void gainExp(int value) {
        gainExp(value, 0);
        getOwner().sendMessage(Lang.translate("%user.pet.gain.exp%")
                .replace("{Name}", getName())
                .replace("{value}", String.valueOf(value)));
    }

    private void updateNameTag() {
        if(this.getPassenger() != null) {
            setNameTag("");
        }else {
            setNameTag(ConfigManager.getPetPrefix(this));
        }
    }

    private void updateScale() {
        int maxLv = PetManager.getMaxLv(type);
        double rate = ((double) lv) / ((double) maxLv);
        double result = getMinScale() + (rate * (getMaxScale() - getMinScale()));
        setScale((float) result);
        if(isInLineup) {
            /* do not refresh this.scale */
            this.setDataProperty(new FloatEntityData(38, (float) (result + getOwner().getLineup().getScaleAdd())));
            this.recalculateBoundingBox();
        }
    }

    public void healHP(int amount) {
        heal((float) amount);
        if(ConfigManager.showAttack()) {
            Tools.createHealParticle(this, amount, Tools.HealType.HP);
        }
    }

    public void healSP(int amount) {
        this.sp = Math.min(getMaxSP(), getSp() + amount);
        if(ConfigManager.showAttack()) {
            Tools.createHealParticle(this, amount, Tools.HealType.SP);
        }
    }

    public void autoSkill() {
        int index = new Random().nextInt(3);
        skill(getSkills()[index]);
    }

    public void skill(BaseSkill skill) {
        if(preDead) return;
        if(skill == null) return;
        if(!canSkill) return;
        if(target == null) {
            getOwner().getPlayer().sendTip(Lang.translate("%user.pet.no.target%").replace("{Name}", getName()));
            return;
        }
        canSkill = false;
        if(getSp() < skill.getSpCost()) {
            getOwner().sendMessage(Lang.translate("%user.pet.no.sp%").replace("{Name}", getName())
                    .replace("{skillName}", skill.getName()));
            return;
        }
        if(this.distanceSquared(target) <= Math.pow(skill.getRange(), 2)) {
            skill.work(this, target);
            /* 造成伤害*/
            target.attack(skill.getDamage());
            if(ConfigManager.showAttack()){
                Tools.createDamageParticle(target, skill.getDamage(), true);
            }
            setSp(getSp() - skill.getSpCost());
            getOwner().sendMessage(Lang.translate("%user.pet.go.skill%").replace("{Name}", getName())
                    .replace("{skillName}", skill.getName()));
            Server.getInstance().getScheduler().scheduleDelayedTask(new Task() {
                @Override
                public void onRun(int i) {
                    int amount = (int) ((1 - getSpLossRate()) * skill.getSpCost());
                    healSP(amount);
                    getOwner().sendMessage(Lang.translate("%user.pet.sp.recover%").replace("{Name}", getName())
                    .replace("{amount}", String.valueOf(amount)));
                }
            }, getSpRecoverRate() * 20);
        }else {
            getOwner().sendMessage(Lang.translate("%user.pet.skill.distance%")
            .replace("{Name}", getName()).replace("{skillName}", skill.getName()));
        }
    }

    public String getInfo() {

        int attack = (int) (getAttack() + attackAdd);
        double attackSpeed = (1D / getAttackSpeed());
        double defenceRate = getDefenceRate() + defenceRateAdd;

        return "名称: " + getName() + "\n"
                + "类别: " + getType() + "\n"
                + "属性: " + getAttribute().toString() + "\n"
                + "等级: " + getLv() + "/" + PetManager.getMaxLv(getType()) + "\n"
                + "经验: " + getExp() + "/" + getMaxExp() + "\n"
                + "SP: " + getSp() + "/" + getMaxSP() + "\n"
                + "血量: " + (int) getHealth() + "/" + getMaxHealth() + "\n"
                + "攻击: " + attack + "\n"
                + "攻速: " + String.format("%.1f", (attackSpeed)) + "\n"
                + "防御: " + defenceRate * 100 + "%"  + "\n"
                + "暴击率: " + (getCritRate() + critRateAdd) * 100 + "%" + "  幸运值加成: " + String.format("%.1f", (getLuckilyUpRate() * 100)) + "%" + "\n"
                + "暴击倍率: " + (getCritTimeRate() + critTimeRateAdd) * 100 + "%" + "\n"
                + "SP恢复速率: " + String.format("%.1f", 100F / ((float) getSpRecoverRate())) + "\n"
                + "SP损失率: " + getSpLossRate() * 100 + "%" + "\n"
                + "食物: " + Tools.convertItem(getFoodRaw().toString()) + "\n"
                + "技能: " + skillNames.toString();
    }

    //todo animation supported
    public void playAnimation(String animationType){
        AnimationController.sendAnimate(animationType, this);
    }

    private float getLuckilyUpRate() {
        double rate = ((double) getOwner().getLuckRate()) / 100D;
        return (float) (rate * ConfigManager.getMaxLuckilyUpRate());
    }

    public String[] getFoodAsString() {
        String[] foodStrs = new String[getFoodItems().size()];
        for(int i = 0; i < getFoodItems().size(); i ++) {
            foodStrs[i] = getFoodItems().get(i).getName();
        }
        return foodStrs;
    }

    @Override
    public Vector3f getMountedOffset(Entity entity) {
        return seatPosition;
    }

    public boolean getAutoSkill() {
        return autoSkill;
    }

}
