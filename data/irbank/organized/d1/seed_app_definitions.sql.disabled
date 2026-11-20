INSERT OR REPLACE INTO diagnosis_bands(min_score,max_score,grade,title,comment) VALUES
(90,100,'S','非常に健全','分散・増配力・利回りのバランスが非常に良好です。現在の構成を維持しつつ、買い増し時は集中度の上昇に注意してください。'),
(80,89,'A','健全','主力銘柄の増配実績が強固で、利回りも良好です。特定セクターへの偏りを抑えれば、より安定した配当ポートフォリオになります。'),
(70,79,'B','概ね良好','配当水準は良好ですが、一部銘柄への依存がやや高めです。買い増しでは異なる業種を優先すると安定性が高まります。'),
(60,69,'C','要点検','配当利回りは確保できていますが、減配耐性または分散に弱さがあります。配当性向と利益推移を確認してください。'),
(0,59,'D','見直し推奨','高利回り銘柄への偏りや業績変動リスクが目立ちます。減配余地と集中リスクを優先して見直してください。');

DELETE FROM valuation_scenarios;
WITH RECURSIVE
per(v) AS (VALUES(10.5),(12.0),(14.0),(16.0),(18.0)),
pbr(v) AS (VALUES(1.2),(1.5),(1.8),(2.2),(3.0),(9.9),(99.0),(999.0),(9999.0),(99999.0)),
rows AS (
  SELECT ROW_NUMBER() OVER (ORDER BY per.v, pbr.v) id, per.v per_max, pbr.v pbr_max
  FROM per CROSS JOIN pbr
)
INSERT INTO valuation_scenarios(id,per_max,pbr_max,title,badge,comment)
SELECT id, per_max, pbr_max,
  CASE WHEN per_max <= 12.0 AND pbr_max <= 1.5 THEN '王道大型バリュー健全圏'
       WHEN per_max <= 16.0 AND pbr_max <= 2.2 THEN '標準バリュー圏'
       ELSE '割高警戒圏' END,
  CASE WHEN per_max <= 12.0 AND pbr_max <= 1.5 THEN '絶好の買増し検討期'
       WHEN per_max <= 16.0 AND pbr_max <= 2.2 THEN '分割買い検討'
       ELSE '価格待ち' END,
  CASE WHEN per_max <= 12.0 AND pbr_max <= 1.5 THEN 'PER・PBRとも市場平均を大きく下回る水準です。収益性と配当余力に問題がなければ、中長期の買い増し候補として評価できます。'
       WHEN per_max <= 16.0 AND pbr_max <= 2.2 THEN '割安感はありますが、決算と利益成長を確認しながら段階的に買い増す水準です。'
       ELSE '指標面では割安感が乏しいため、利益成長で正当化できるかを確認し、価格下落を待つ余地があります。' END
FROM rows;
INSERT OR REPLACE INTO app_definition_documents(definition_key,value_json) VALUES ('stock-analysis-definitions:v1','{"metric_rules":[{"bands":[{"label":"割安","score":100,"max":10},{"label":"やや割安","score":80,"max":15},{"label":"適正","score":65,"max":20},{"label":"やや割高","score":45,"max":30},{"label":"割高","score":25,"max":99999}],"label":"PER","key":"per","weight":15},{"bands":[{"label":"資産面で割安","score":100,"max":0.8},{"label":"割安","score":85,"max":1.2},{"label":"適正","score":65,"max":2.0},{"label":"高め","score":45,"max":3.5},{"label":"高評価","score":25,"max":99999}],"label":"PBR","key":"pbr","weight":10},{"bands":[{"label":"余力大","score":85,"max":30},{"label":"健全","score":100,"max":50},{"label":"やや高め","score":70,"max":70},{"label":"高め","score":45,"max":90},{"label":"要警戒","score":20,"max":99999}],"label":"配当性向","key":"payout_ratio","weight":20},{"bands":[{"score":100,"min":60,"label":"非常に高い"},{"score":85,"min":40,"label":"高い"},{"score":65,"min":25,"label":"標準"},{"score":45,"min":15,"label":"低め"},{"score":25,"min":-99999,"label":"低い"}],"label":"自己資本比率","key":"equity_ratio","weight":15},{"bands":[{"score":100,"min":15,"label":"高収益"},{"score":85,"min":10,"label":"良好"},{"score":65,"min":5,"label":"標準"},{"score":45,"min":2,"label":"低め"},{"score":20,"min":-99999,"label":"低収益"}],"label":"営業利益率","key":"operating_margin","weight":15},{"bands":[{"score":100,"min":10,"label":"非常に強い"},{"score":85,"min":5,"label":"強い"},{"score":65,"min":0,"label":"確保"},{"score":40,"min":-5,"label":"弱い"},{"score":20,"min":-99999,"label":"赤字"}],"label":"FCF余力","key":"fcf_margin","weight":15},{"bands":[{"score":100,"min":10,"label":"長期増配"},{"score":85,"min":5,"label":"増配継続"},{"score":70,"min":3,"label":"実績あり"},{"score":55,"min":1,"label":"短期実績"},{"score":40,"min":-99999,"label":"実績限定"}],"label":"連続増配","key":"dividend_growth_years","weight":10}],"overall_bands":[{"title":"非常に良好","min_score":90,"grade":"S","comment":"割安性、財務、配当、収益、キャッシュ、増配実績を総合すると非常に良好です。","max_score":100},{"title":"良好","min_score":80,"grade":"A","comment":"総合的に良好です。弱い指標を確認しながら価格水準を見て判断できます。","max_score":89},{"title":"概ね良好","min_score":70,"grade":"B","comment":"大きな弱点は限定的ですが、財務または配当の一部に確認余地があります。","max_score":79},{"title":"注意点あり","min_score":60,"grade":"C","comment":"複数の指標に注意点があります。買い増し前に弱い項目の推移を確認してください。","max_score":69},{"title":"要点検","min_score":0,"grade":"D","comment":"財務、配当、収益または価格のいずれかに明確な弱さがあります。","max_score":59}],"version":1}');
INSERT OR REPLACE INTO app_definition_documents(definition_key,value_json) VALUES ('watchlist-analysis-definitions:v1','{"display_fields":["buy_zone","overall_grade","valuation_label","risk_flag"],"buy_zone":[{"comment":"現在利回りが設定目標に到達しています。","label":"目標到達","status":"TARGET","min_ratio":1.0},{"comment":"目標利回りまで5%以内です。","label":"買場接近","status":"NEAR","min_ratio":0.95},{"comment":"目標水準へ近づいています。","label":"監視圏","status":"WATCH","min_ratio":0.85},{"comment":"設定した目標利回りにはまだ距離があります。","label":"価格待ち","status":"WAIT","min_ratio":0.0}],"version":1}');
