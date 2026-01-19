-- 1. 개발용 기본 사용자
INSERT INTO users (
    user_id,
    user_email,
    user_name,
    profile_url,
    created_at
) VALUES (
             1,
             'lion01@gmail.com',
             'Lion',
             'https://example.com/profiles/lion.png',
             NOW()
         )
    ON CONFLICT (user_id) DO NOTHING;


-- 2. 개발용 기본 프로젝트 1개
INSERT INTO projects (
    project_id,
    user_id,
    project_name,
    project_description,
    project_created_at,
    project_updated_at
) VALUES (
             1,
             1,  -- user_id = 1 (Lion)
             'MOA 개발 프로젝트1',
             'MOA 서비스 개발을 위한 프로젝트1',
             NOW(),
             NOW()
         ),
         (
             2,
             1,
             'MOA 개발 프로젝트2',
             'MOA 서비스 개발을 위한 프로젝트2',
             NOW(),
             NOW()
         ),
         (
             3,
             1,
             'MOA 개발 프로젝트3',
             'MOA 서비스 개발을 위한 프로젝트3',
             NOW(),
             NOW()
         ),
         (
             15,
             1,
             'MOA',
             'MOA 서비스 개발',
             NOW(),
             NOW()
         ),
         (
             16,
             1,
             'AI 대전 해커톤',
             '해커톤 프로젝트',
             NOW(),
             NOW()
         )

    ON CONFLICT (project_id) DO NOTHING;

-- scraps (project_id=15, stage=설계) - 무한 스크롤/커서 테스트용 (captured_at 내림차순)
INSERT INTO scraps (
    scrap_id,
    project_id,
    user_id,
    raw_html,
    subtitle,
    stage,
    memo,
    ai_source,
    ai_source_url,
    rec_method,
    user_rec_project,
    user_rec_stage,
    user_rec_subtitle,
    captured_at
) VALUES
      (
          500,
          15,
          1,
          '<h1>Header 1</h1><p>Hello <b>World</b>!!</p><ul><li>A</li><li>B</li></ul>',
          'API 설계 초안',
          '설계',
          NULL,
          'GPT',
          'https://example.com',
          'LLM',
          true,
          false,
          true,
          NOW() - INTERVAL '4 days'
      ),
      (
          501,
          15,
          1,
          '<h2>Auth</h2><p>X-User-Id 헤더 처리 방식 정리</p>',
          '인증/헤더 정책',
          '설계',
          '필터 vs 컨트롤러 방식 비교 필요',
          'GPT',
          'https://example.com/auth',
          'LLM',
          true,
          true,
          false,
          NOW() - INTERVAL '3 days'
      ),
      (
          502,
          15,
          1,
          '<h2>Paging</h2><p>cursor 기반 키셋 페이징 정리</p>',
          '무한 스크롤 설계',
          '설계',
          NULL,
          'GPT',
          'https://example.com/paging',
          'FALLBACK_RECENT',
          false,
          true,
          true,
          NOW() - INTERVAL '2 days'
      ),
      (
          503,
          15,
          1,
          '<h2>DB</h2><p>scraps 인덱스 전략</p>',
          'DB 인덱스 메모',
          '설계',
          'user_id + project_id + stage + captured_at 조합',
          'GPT',
          'https://example.com/db',
          'NONE',
          false,
          false,
          false,
          NOW() - INTERVAL '1 days'
      )
    ON CONFLICT (scrap_id) DO NOTHING;

-- scraps (project_id=7) - recent-context에서 "프로젝트별 최신 1개" 테스트용
INSERT INTO scraps (
    scrap_id,
    project_id,
    user_id,
    raw_html,
    subtitle,
    stage,
    memo,
    ai_source,
    ai_source_url,
    rec_method,
    user_rec_project,
    user_rec_stage,
    user_rec_subtitle,
    captured_at
) VALUES
      (
          510,
          16,
          1,
          '<h1>Hackathon</h1><p>기획 초안</p>',
          '아이디어 정리',
          '기획',
          NULL,
          'GPT',
          'https://example.com/hackathon',
          'LLM',
          true,
          true,
          true,
          NOW() - INTERVAL '6 days'
      ),
      (
          511,
          16,
          1,
          '<p>최신 스크랩(이 값이 recent-context에 잡혀야 함)</p>',
          '최신 기획 메모',
          '기획',
          'recent-context 확인용',
          'GPT',
          'https://example.com/hackathon/latest',
          'LLM',
          false,
          true,
          false,
          NOW() - INTERVAL '12 hours'
      )
    ON CONFLICT (scrap_id) DO NOTHING;
