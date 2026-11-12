package org.dataanaliticsent.Calidad.Infraestructura;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels.*;

/**
 * Todas las operaciones de negocio se ejecutan dentro de ProjectTransaction.
 */
@Repository
public class QualityRepository {
    private final EntityManager em;

    public QualityRepository(EntityManager em) {
        this.em = em;
    }

    public <T> T save(T value) {
        em.persist(value);
        return value;
    }

    public void flush() {
        em.flush();
    }

    public record PendingCounts(long project, long user) {}

    public PendingCounts pendingCounts(UUID project) {
        Object[] row = (Object[]) em.createNativeQuery("select project_pending,user_pending from m2.pending_evaluation_counts(:project)")
                .setParameter("project",project).getSingleResult();
        return new PendingCounts(((Number)row[0]).longValue(), ((Number)row[1]).longValue());
    }

    @Transactional(readOnly = true)
    public long anonymousSessionCount(long now) {
        return ((Number) em.createNativeQuery("select count(*) from m2.spring_session s where s.expiry_time>:now"
                + " and not exists (select 1 from m2.spring_session_attributes a where a.session_primary_id=s.primary_id and a.attribute_name='m2_user')")
                .setParameter("now", now).getSingleResult()).longValue();
    }

    public void refresh(Object value) {
        em.refresh(value);
    }

    public <T> Optional<T> find(Class<T> type, UUID id, UUID project) {
        return em.createQuery("select x from " + type.getSimpleName() + " x where x.id=:id and x.projectId=:project", type)
                .setParameter("id", id).setParameter("project", project).getResultStream().findFirst();
    }

    public <T> T lock(T entity) {
        em.lock(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }

    public <T> T lockAndRefresh(T entity) {
        em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }

    /** Estabiliza los permisos y membresías antes de bloquear proyectos y cuentas. */
    public void lockAdministrationAccounts(UUID actor, UUID target) {
        List.of(actor, target).stream().map(UUID::toString).distinct().sorted().forEach(id ->
                em.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:account, 0))")
                        .setParameter("account", "m2.admin.account:" + id).getSingleResult());
    }

    public boolean hasOtherActiveAdministrator(UUID project, UUID excludedUser) {
        return em.createQuery("select count(r) from RoleAssignment r, UserAccount u where r.userId=u.id"
                        + " and r.projectId=:project and r.roleName='SYSTEM_ADMIN' and r.assetScopeId is null"
                        + " and u.active=true and u.id<>:excludedUser", Long.class)
                .setParameter("project", project).setParameter("excludedUser", excludedUser).getSingleResult() > 0;
    }

    public <T> List<T> list(Class<T> type, UUID project, UUID asset, int offset, int size) {
        var q = em.createQuery("select x from " + type.getSimpleName() + " x where x.projectId=:project"
                + (asset == null ? "" : " and x.assetId=:asset") + " order by x.createdAt desc, x.id", type).setParameter("project", project);
        if (asset != null) q.setParameter("asset", asset);
        return q.setFirstResult(offset).setMaxResults(size).getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> user(UUID id) {
        return Optional.ofNullable(em.find(UserAccount.class, id));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> userByEmailHash(String hash) {
        return em.createQuery("select u from UserAccount u where u.emailLookupHash=:hash", UserAccount.class)
                .setParameter("hash", hash).getResultStream().findFirst();
    }

    public Optional<Project> project(UUID id) {
        return Optional.ofNullable(em.find(Project.class, id));
    }

    @Transactional(readOnly = true)
    public List<RoleAssignment> roles(UUID user, UUID project) {
        var query = em.createQuery("select r from RoleAssignment r where r.userId=:user" + (project == null ? "" : " and r.projectId=:project") + " order by r.createdAt,r.id", RoleAssignment.class).setParameter("user", user);
        if (project != null) query.setParameter("project", project);
        return query.getResultList();
    }

    public List<RoleAssignment> projectRoles(UUID project) {
        return em.createQuery("select r from RoleAssignment r where r.projectId=:project order by r.createdAt,r.id", RoleAssignment.class).setParameter("project", project).getResultList();
    }

    public void removeRole(RoleAssignment role) {
        em.remove(role);
    }

    public List<RuleVersion> versions(UUID project, UUID rule) {
        return em.createQuery("select v from RuleVersion v where v.projectId=:project and v.ruleId=:rule order by v.versionNumber desc", RuleVersion.class)
                .setParameter("project", project).setParameter("rule", rule).getResultList();
    }

    public List<RuleVersion> selectedVersions(UUID project, UUID evaluation) {
        return em.createQuery("select v from RuleVersion v, EvaluationRule s where s.projectId=:project and s.evaluationId=:evaluation and v.id=s.ruleVersionId and v.projectId=s.projectId order by v.id", RuleVersion.class)
                .setParameter("project", project).setParameter("evaluation", evaluation).getResultList();
    }

    public Map<UUID,RuleVersion> latestVersions(UUID project,List<UUID> ids) {
        Map<UUID,RuleVersion> result=new HashMap<>();if(ids.isEmpty())return result;
        em.createQuery("select v from RuleVersion v where v.projectId=:project and v.ruleId in :ids and v.versionNumber=(select max(other.versionNumber) from RuleVersion other where other.projectId=v.projectId and other.ruleId=v.ruleId)",RuleVersion.class)
            .setParameter("project",project).setParameter("ids",ids).getResultList().forEach(v -> result.put(v.ruleId,v));return result;
    }
    public List<QualityRule> rules(UUID project,UUID asset,String search,Boolean enabled,int offset,int size) {
        var query=em.createQuery("select r from QualityRule r where r.projectId=:project"+(asset==null?"":" and r.assetId=:asset")+(search==null||search.isBlank()?"":" and locate(:search,lower(r.name))>0")+(enabled==null?"":" and r.enabled=:enabled")+" order by r.createdAt desc,r.id",QualityRule.class).setParameter("project",project);
        if(asset!=null)query.setParameter("asset",asset);if(search!=null&&!search.isBlank())query.setParameter("search",search.trim().toLowerCase(Locale.ROOT));if(enabled!=null)query.setParameter("enabled",enabled);
        return query.setFirstResult(offset).setMaxResults(size).getResultList();
    }
    public Optional<InputRef> latestInput(UUID project,UUID asset) {
        return em.createQuery("select i from InputRef i where i.projectId=:project and i.assetId=:asset order by i.createdAt desc,i.id",InputRef.class)
            .setParameter("project",project).setParameter("asset",asset).setMaxResults(1).getResultStream().findFirst();
    }

    public List<InputRef> expiredCsvInputs(UUID project, java.time.Instant now, java.time.Instant cutoff, int limit) {
        return em.createQuery("select i from InputRef i where i.projectId=:project"
                        + " and i.protectedObjectRef like 'local-csv:%' and i.availability<>'DELETED'"
                        + " and (i.acquiredAt<=:cutoff or i.expiresAt<=:now) order by i.acquiredAt,i.id", InputRef.class)
                .setParameter("project", project).setParameter("now", now).setParameter("cutoff", cutoff)
                .setMaxResults(limit).getResultList();
    }
    public Map<UUID,InputRef> inputs(UUID project,List<UUID> ids) {
        Map<UUID,InputRef> result=new HashMap<>();if(ids.isEmpty())return result;
        em.createQuery("select i from InputRef i where i.projectId=:project and i.id in :ids",InputRef.class).setParameter("project",project).setParameter("ids",ids).getResultList().forEach(i -> result.put(i.id,i));return result;
    }
    public record EvaluationFilter(UUID asset,java.time.Instant from,java.time.Instant until,String state,List<UUID> scopes) {}
    private <T> jakarta.persistence.TypedQuery<T> evaluationQuery(UUID project,EvaluationFilter f,String select,Class<T> type) {
        StringBuilder jpql=new StringBuilder(select+" from Evaluation e where e.projectId=:project");Map<String,Object> values=new HashMap<>();values.put("project",project);
        if(f.asset()!=null){jpql.append(" and e.assetId=:asset");values.put("asset",f.asset());}
        if(f.from()!=null){jpql.append(" and e.createdAt>=:from");values.put("from",f.from());}
        if(f.until()!=null){jpql.append(" and e.createdAt<:until");values.put("until",f.until());}
        if(f.state()!=null){jpql.append(" and e.status=:state");values.put("state",f.state());}
        if(f.scopes()!=null){if(f.scopes().isEmpty())jpql.append(" and 1=0");else{jpql.append(" and e.assetId in :scopes");values.put("scopes",f.scopes());}}
        if(type==Evaluation.class)jpql.append(" order by e.createdAt desc,e.id");
        var query=em.createQuery(jpql.toString(),type);values.forEach((key,value) -> query.setParameter(key,value));return query;
    }
    public List<Evaluation> evaluations(UUID project,EvaluationFilter filter,int offset,int size){return evaluationQuery(project,filter,"select e",Evaluation.class).setFirstResult(offset).setMaxResults(size).getResultList();}
    public long evaluationCount(UUID project,EvaluationFilter filter){return evaluationQuery(project,filter,"select count(e)",Long.class).getSingleResult();}

    public List<RuleResult> results(UUID project, UUID evaluation) {
        return em.createQuery("select r from RuleResult r where r.projectId=:project and r.evaluationId=:evaluation order by r.id", RuleResult.class)
                .setParameter("project", project).setParameter("evaluation", evaluation).getResultList();
    }

    /** Una ejecución completada por dimensión, sin recorrer/paginar todo el historial en el cliente. */
    public List<RuleResult> latestDimensionResults(UUID project, UUID asset) {
        List<?> rows = em.createNativeQuery("""
                with latest_dimension as (
                    select distinct on (v.dimension) v.dimension, e.id
                    from m2.evaluations e
                    join m2.rule_results r on r.project_id=e.project_id and r.asset_id=e.asset_id and r.evaluation_id=e.id
                    join m2.rule_versions v on v.project_id=r.project_id and v.asset_id=r.asset_id and v.id=r.rule_version_id
                    where e.project_id=:project and e.asset_id=:asset and e.status='COMPLETED'
                    order by v.dimension, e.created_at desc, e.id
                )
                select r.* from latest_dimension d
                join m2.rule_results r on r.evaluation_id=d.id and r.project_id=:project and r.asset_id=:asset
                join m2.rule_versions v on v.id=r.rule_version_id and v.project_id=r.project_id and v.asset_id=r.asset_id
                where v.dimension=d.dimension
                order by v.dimension, r.id
                """, RuleResult.class).setParameter("project", project).setParameter("asset", asset).getResultList();
        return rows.stream().map(RuleResult.class::cast).toList();
    }

    public Optional<Evaluation> idempotent(UUID project, UUID actor, UUID key) {
        return em.createQuery("select e from Evaluation e where e.projectId=:project and e.requestedBy=:actor and e.idempotencyKey=:key", Evaluation.class)
                .setParameter("project", project).setParameter("actor", actor).setParameter("key", key).getResultStream().findFirst();
    }

    public List<IncidentEvent> events(UUID project, UUID incident) {
        return em.createQuery("select e from IncidentEvent e where e.projectId=:project and e.incidentId=:incident order by e.createdAt,e.id", IncidentEvent.class)
                .setParameter("project", project).setParameter("incident", incident).getResultList();
    }

    public Optional<InputRef> inputByRead(UUID project, UUID read) {
        return em.createQuery("select i from InputRef i where i.projectId=:project and i.readId=:read", InputRef.class)
            .setParameter("project",project).setParameter("read",read).getResultStream().findFirst();
    }

    public List<InputRef> csvInputsByHash(UUID project,String hash) {
        return em.createQuery("select i from InputRef i where i.projectId=:project and i.contentSha256=:hash and i.protectedObjectRef like 'local-csv:%' and i.availability='AVAILABLE' order by i.createdAt,i.id",InputRef.class)
            .setParameter("project",project).setParameter("hash",hash).getResultList();
    }

    public record IncidentFilter(UUID asset,UUID evaluation,String status,String severity,String dimension,String search,List<UUID> scopes) {}
    private <T> jakarta.persistence.TypedQuery<T> incidentQuery(UUID project,IncidentFilter f,String select,Class<T> type) {
        StringBuilder jpql=new StringBuilder(select+" from Incident i,RuleResult rr,RuleVersion v,QualityRule q where i.projectId=:project and rr.projectId=i.projectId and rr.id=i.ruleResultId and v.projectId=i.projectId and v.id=rr.ruleVersionId and q.projectId=i.projectId and q.id=v.ruleId");
        Map<String,Object> parameters=new HashMap<>();parameters.put("project",project);
        if(f.asset()!=null){jpql.append(" and i.assetId=:asset");parameters.put("asset",f.asset());}
        if(f.evaluation()!=null){jpql.append(" and i.evaluationId=:evaluation");parameters.put("evaluation",f.evaluation());}
        if(f.status()!=null){jpql.append(" and i.status=:status");parameters.put("status",f.status());}
        if(f.severity()!=null){jpql.append(" and i.severity=:severity");parameters.put("severity",f.severity());}
        if(f.dimension()!=null){jpql.append(" and v.dimension=:dimension");parameters.put("dimension",f.dimension());}
        if(f.search()!=null&&!f.search().isBlank()){
            jpql.append(" and (locate(:search,lower(q.name))>0 or locate(:search,lower(i.fieldName))>0 or locate(:search,lower(i.recordReference))>0 or locate(:search,lower(i.problemCode))>0)");
            parameters.put("search",f.search().trim().toLowerCase(Locale.ROOT));
        }
        if(f.scopes()!=null){if(f.scopes().isEmpty())jpql.append(" and 1=0");else{jpql.append(" and i.assetId in :scopes");parameters.put("scopes",f.scopes());}}
        if(type==Incident.class)jpql.append(" order by i.createdAt desc,i.id");
        var query=em.createQuery(jpql.toString(),type);parameters.forEach((key,value) -> query.setParameter(key,value));return query;
    }
    public List<Incident> incidents(UUID project,IncidentFilter filter,int offset,int size) {
        return incidentQuery(project,filter,"select i",Incident.class).setFirstResult(offset).setMaxResults(size).getResultList();
    }
    public long incidentCount(UUID project,IncidentFilter filter) {
        return incidentQuery(project,filter,"select count(i)",Long.class).getSingleResult();
    }
    public Map<UUID,Map<String,Object>> incidentRules(UUID project,List<UUID> ids) {
        Map<UUID,Map<String,Object>> result=new HashMap<>();if(ids.isEmpty())return result;
        var rows=em.createQuery("select i.id,q.name,v.dimension,v.id from Incident i,RuleResult r,RuleVersion v,QualityRule q where i.projectId=:project and i.id in :ids and r.id=i.ruleResultId and r.projectId=i.projectId and v.id=r.ruleVersionId and v.projectId=i.projectId and q.id=v.ruleId and q.projectId=i.projectId",Object[].class).setParameter("project",project).setParameter("ids",ids).getResultList();
        for(var row:rows)result.put((UUID)row[0],Map.of("rule_name",row[1],"dimension",row[2],"rule_version_id",row[3]));
        return result;
    }

    public Optional<PasswordResetToken> reset(String hash) {
        return em.createQuery("select t from PasswordResetToken t where t.tokenHash=:hash", PasswordResetToken.class)
                .setParameter("hash", hash).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst();
    }

    public Optional<UUID> resetUserId(String hash) {
        return em.createQuery("select t.userId from PasswordResetToken t where t.tokenHash=:hash", UUID.class)
                .setParameter("hash", hash).getResultStream().findFirst();
    }

    public void consumeResetTokens(UUID user, java.time.Instant usedAt) {
        em.createQuery("update PasswordResetToken t set t.usedAt=:usedAt where t.userId=:user and t.usedAt is null")
                .setParameter("usedAt", usedAt).setParameter("user", user).executeUpdate();
    }

    public List<Evaluation> unfinished(UUID project) {
        return em.createQuery("select e from Evaluation e where e.projectId=:project and e.status in ('QUEUED','RUNNING') order by e.createdAt,e.id", Evaluation.class)
                .setParameter("project", project).setMaxResults(100).getResultList();
    }
}
